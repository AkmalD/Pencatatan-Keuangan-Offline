from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter
from pathlib import Path

CATEGORIES = [
    "Makan & Minum",
    "Transport",
    "Belanja",
    "Tagihan",
    "Hiburan",
    "Gaji/Pemasukan",
    "Kesehatan",
    "Transfer",
]

ALIASES = {
    "rb": "ribu",
    "rbu": "ribu",
    "k": "ribu",
    "jt": "juta",
    "mio": "juta",
    "rp": "rupiah",
    "tf": "transfer",
    "trf": "transfer",
    "grab": "grab",
    "gojek": "gojek",
    "grabfood": "grabfood",
    "gofood": "gofood",
    "e": "e",
    "toll": "tol",
}

STOP_WORDS = {
    "aku",
    "dan",
    "dari",
    "di",
    "idr",
    "juta",
    "ke",
    "mio",
    "rb",
    "rbu",
    "ribu",
    "rp",
    "rupiah",
    "saya",
    "untuk",
    "yang",
}

TOKEN_PATTERN = re.compile(r"[a-z]+|\d+")


def tokenize(text: str) -> list[str]:
    tokens: list[str] = []
    for raw_token in TOKEN_PATTERN.findall(text.lower()):
        if raw_token.isdigit():
            continue
        token = ALIASES.get(raw_token, raw_token)
        if len(token) <= 1 or token in STOP_WORDS:
            continue
        tokens.append(token)
    return tokens


def load_rows(csv_path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    with csv_path.open("r", encoding="utf-8", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        for line_number, row in enumerate(reader, start=2):
            text = row.get("text", "").strip()
            category = row.get("category", "").strip()
            if not text or not category:
                continue
            if category not in CATEGORIES:
                raise ValueError(f"Unknown category at line {line_number}: {category}")
            rows.append((text, category))
    if not rows:
        raise ValueError(f"No training rows found in {csv_path}")
    return rows


def build_model(rows: list[tuple[str, str]], alpha: float = 1.0) -> dict:
    class_counts = Counter(category for _, category in rows)
    token_counts = {category: Counter() for category in CATEGORIES}
    vocabulary: set[str] = set()

    for text, category in rows:
        tokens = tokenize(text)
        token_counts[category].update(tokens)
        vocabulary.update(tokens)

    sorted_vocabulary = sorted(vocabulary)
    total_documents = len(rows)
    class_denominator = total_documents + alpha * len(CATEGORIES)

    class_log_priors = {
        category: math.log((class_counts[category] + alpha) / class_denominator)
        for category in CATEGORIES
    }

    unknown_log_probs: dict[str, float] = {}
    token_log_probs: dict[str, dict[str, float]] = {}
    vocabulary_denominator_size = len(sorted_vocabulary) + 1

    for category in CATEGORIES:
        total_tokens = sum(token_counts[category].values())
        denominator = total_tokens + alpha * vocabulary_denominator_size
        unknown_log_probs[category] = math.log(alpha / denominator)
        token_log_probs[category] = {
            token: math.log((count + alpha) / denominator)
            for token, count in sorted(token_counts[category].items())
        }

    return {
        "version": 1,
        "model_type": "multinomial_naive_bayes",
        "categories": CATEGORIES,
        "aliases": ALIASES,
        "stop_words": sorted(STOP_WORDS),
        "class_log_priors": class_log_priors,
        "unknown_log_probs": unknown_log_probs,
        "token_log_probs": token_log_probs,
        "metadata": {
            "alpha": alpha,
            "training_rows": total_documents,
            "vocabulary_size": len(sorted_vocabulary),
        },
    }


def predict(model: dict, text: str) -> tuple[str, float]:
    tokens = tokenize(text)
    if not tokens:
        return "Belanja", 0.0

    token_counts = Counter(tokens)
    scores: dict[str, float] = {}
    for category in model["categories"]:
        score = model["class_log_priors"][category]
        category_scores = model["token_log_probs"][category]
        unknown_score = model["unknown_log_probs"][category]
        for token, count in token_counts.items():
            score += category_scores.get(token, unknown_score) * count
        scores[category] = score

    category, winning_score = max(scores.items(), key=lambda item: item[1])
    denominator = sum(math.exp(score - winning_score) for score in scores.values())
    confidence = 0.0 if denominator == 0.0 else 1.0 / denominator
    return category, confidence


def evaluate(model: dict, rows: list[tuple[str, str]]) -> float:
    correct = 0
    for text, expected_category in rows:
        predicted_category, _ = predict(model, text)
        if predicted_category == expected_category:
            correct += 1
    return correct / len(rows)


def export_model(model: dict, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(model, ensure_ascii=False, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    root_dir = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Train local transaction category model.")
    parser.add_argument("--csv", type=Path, default=root_dir / "ml" / "training_data.csv")
    parser.add_argument("--out", type=Path, default=root_dir / "app" / "src" / "main" / "assets" / "category_model.json")
    parser.add_argument("--alpha", type=float, default=1.0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rows = load_rows(args.csv)
    model = build_model(rows, alpha=args.alpha)
    accuracy = evaluate(model, rows)
    export_model(model, args.out)

    metadata = model["metadata"]
    print(f"Training rows: {metadata['training_rows']}")
    print(f"Vocabulary size: {metadata['vocabulary_size']}")
    print(f"Training accuracy: {accuracy:.2%}")
    print(f"Model exported to: {args.out}")


if __name__ == "__main__":
    main()
