import { AtsScoreResult } from '../../shared/models/application-generator.models';
import { downloadThemedCvPdf } from '../../shared/utils/themed-cv-pdf';

const STOP_WORDS = new Set([
  'about', 'above', 'after', 'again', 'against', 'along', 'among', 'and', 'are', 'because', 'been',
  'before', 'being', 'below', 'between', 'both', 'could', 'doing', 'each', 'from', 'have', 'into',
  'just', 'more', 'most', 'other', 'over', 'same', 'some', 'such', 'that', 'their', 'there', 'these',
  'they', 'this', 'those', 'under', 'very', 'what', 'when', 'where', 'which', 'while', 'with', 'your',
]);

function extractKeywords(text: string): string[] {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, ' ')
    .split(/\s+/)
    .map((word) => word.trim())
    .filter((word) => word.length >= 4 && !STOP_WORDS.has(word));
}

export function computeAtsScore(jobDescription: string, cvText: string): AtsScoreResult {
  const keywords = Array.from(new Set(extractKeywords(jobDescription)));
  const cv = cvText.toLowerCase();

  if (!keywords.length) {
    return { score: 0, matchedKeywords: [], missingKeywords: [] };
  }

  const matchedKeywords = keywords.filter((keyword) => cv.includes(keyword));
  const missingKeywords = keywords.filter((keyword) => !cv.includes(keyword));
  const score = Math.min(100, Math.round((matchedKeywords.length / keywords.length) * 100));

  return {
    score,
    matchedKeywords: matchedKeywords.slice(0, 10),
    missingKeywords: missingKeywords.slice(0, 10),
  };
}

export function downloadSimplePdf(filename: string, title: string, content: string): void {
  downloadThemedCvPdf(filename, title, content || 'No content', 'classic');
}

export { downloadThemedCvPdf } from '../../shared/utils/themed-cv-pdf';
