"""Dependency-free smoke tests for the chatbot service.

Run in the container:
  docker compose exec chatbot-service python -m unittest discover -s tests -v
"""
import math
import unittest

from app import embedder


class EmbedderTest(unittest.TestCase):
    def test_dimension_is_384(self):
        self.assertEqual(384, len(embedder.embed("coal production 2024-25")))

    def test_deterministic(self):
        self.assertEqual(embedder.embed("coal"), embedder.embed("coal"))

    def test_unit_norm(self):
        vec = embedder.embed("coal production dispatch")
        norm = math.sqrt(sum(v * v for v in vec))
        self.assertAlmostEqual(1.0, norm, places=6)

    def test_related_text_scores_higher_than_unrelated(self):
        q = embedder.embed("coal production 2024-25")
        near = embedder.embed("Field: Coal production. Value: 773.8 MT. Period: 2024-25.")
        far = embedder.embed("Field: Auditor Report. Value: 76. Page.")
        dot = lambda a, b: sum(x * y for x, y in zip(a, b))
        self.assertGreater(dot(q, near), dot(q, far))

    def test_sql_literal_format(self):
        lit = embedder.to_sql([0.5, -1.0])
        self.assertTrue(lit.startswith("["))
        self.assertTrue(lit.endswith("]"))
        self.assertIn(",", lit)


if __name__ == "__main__":
    unittest.main()
