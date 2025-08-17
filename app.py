from flask import Flask, request, jsonify
from transformers import pipeline, BertTokenizer, BertForSequenceClassification
from googletrans import Translator
import fitz  
import logging
import torch
import spacy
from flask_cors import CORS
from collections import Counter
import numpy as np

nlp = spacy.load("en_core_web_sm")

app = Flask(__name__)
CORS(app)  

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

summarizer = pipeline(
    "summarization",
    model="google/pegasus-xsum",
    device=0 if torch.cuda.is_available() else -1
)


translator = Translator()

MODEL_NAME = "textattack/bert-base-uncased-yelp-polarity"
tokenizer = BertTokenizer.from_pretrained(MODEL_NAME)
model = BertForSequenceClassification.from_pretrained(MODEL_NAME)

rewriter = pipeline("text2text-generation", model="facebook/bart-large-cnn")

@app.route('/translate', methods=['POST'])
def translate():
    try:
        data = request.json
        text = data['text']
        target_lang = data['lang']
        translation = translator.translate(text, dest=target_lang)
        return jsonify({"translated_text": translation.text})
    except Exception as e:
        logger.error(f"Translation error: {str(e)}")
        return jsonify({"error": "Translation failed"}), 500

@app.route('/summarize', methods=['POST'])
def summarize():
    try:
        data = request.json
        text = data['text'].strip()
        if not text:
            return jsonify({"error": "Text cannot be empty"}), 400

        word_count = len(text.split())

        if word_count < 25:
            truncated = " ".join(text.split()[:10])
            return jsonify({
                "summary": f"[Too Short] {truncated}{'...' if word_count > 10 else ''}",
                "note": "Input too short for meaningful summarization"
            })

        max_len = min(180, max(60, word_count // 2))
        min_len = min(120, max(30, word_count // 3))

        summary = summarizer(
            text,
            max_length=max_len,
            min_length=min_len,
            do_sample=False,
            num_beams=4,
            no_repeat_ngram_size=2,
            early_stopping=True
        )

        summary_text = summary[0]['summary_text']

        def calculate_similarity(a, b):
            a_words = set(a.lower().split())
            b_words = set(b.lower().split())
            return len(a_words & b_words) / len(a_words | b_words)

        if (len(summary_text.split()) > word_count * 0.75 or 
            calculate_similarity(text, summary_text) > 0.8):
            # Fallback to top 2 key sentences
            doc = nlp(text)
            sentences = [sent.text.strip() for sent in doc.sents]
            summary_text = "[Key Points] " + " ".join(sentences[:2])

        return jsonify({
            "summary": summary_text,
            "original_length": word_count,
            "summary_length": len(summary_text.split()),
            "compression_ratio": f"{100 - (len(summary_text.split()) / word_count * 100):.1f}%"
        })
    except Exception as e:
        logger.error(f"Summarization error: {str(e)}")
        return jsonify({
            "error": "Failed to generate summary",
            "debug": str(e)
        }), 500

@app.route('/upload-pdf', methods=['POST'])
def upload_pdf():
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "No file selected"}), 400

    try:
        text = ""
        with fitz.open(stream=file.read(), filetype="pdf") as pdf_document:
            for page in pdf_document:
                text += page.get_text()
        logger.info("Successfully extracted text from PDF")
        return jsonify({"text": text})
    except Exception as e:
        logger.error(f"PDF extraction error: {str(e)}")
        return jsonify({"error": "Failed to extract text from PDF"}), 500

@app.route('/rewrite', methods=['POST'])
def rewrite():
    try:
        data = request.json
        text = data.get('text', '')
        style = data.get('style', 'professional').lower()
        
        if not text:
            return jsonify({"error": "Text cannot be empty"}), 400

        # Enhanced style-specific prompts
        style_prompts = {
            "professional": (
                "Rewrite this text in formal business English suitable for a corporate report. "
                "Use professional terminology and complete sentences:\n\n"
                f"{text}"
            ),
            "casual": (
                "Convert this text to casual, conversational English like you're texting a friend. "
                "Use contractions and informal language:\n\n"
                f"{text}"
            ),
            "academic": (
                "Rewrite this in academic writing style with formal tone, "
                "citations in [brackets], and complex sentence structures:\n\n"
                f"{text}"
            ),
            "simplified": (
                "Simplify this text to 6th grade reading level. "
                "Use short sentences and basic vocabulary:\n\n"
                f"{text}"
            )
        }

        if style not in style_prompts:
            return jsonify({"error": "Invalid style specified"}, 400)
        result = rewriter(
            style_prompts[style],
            max_length=1024,
            min_length=60,
            num_beams=5,
            temperature=0.9,  
            top_k=50,
            top_p=0.95,
            repetition_penalty=2.0,
            do_sample=True
        )
        output = result[0]['generated_text']
        if style == "academic" and "[citation]" not in output.lower():
            output += " [Further research needed]"
        elif style == "casual":
            output = output.replace(" cannot ", " can't ").replace(" do not ", " don't ")

        return jsonify({
            "original_text": text,
            "rewritten_text": output,
            "style": style,
            "prompt_used": style_prompts[style]  # For debugging
        })
    except Exception as e:
        logger.error(f"Rewriting error: {str(e)}")
        return jsonify({"error": "Failed to rewrite text"}), 500

if __name__ == '__main__':

    app.run(host='0.0.0.0', port=5000, debug=True)


