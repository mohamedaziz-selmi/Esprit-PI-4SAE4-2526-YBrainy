import { Injectable } from '@angular/core';

export interface GrammarIssue {
  id: string;
  type: 'spelling' | 'punctuation' | 'style';
  message: string;
  example?: string;
}

export interface GrammarResult {
  issues: GrammarIssue[];
  score: number;
}

@Injectable({ providedIn: 'root' })
export class GrammarCheckerService {
  check(text: string): GrammarResult {
    const plain = text.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    const issues: GrammarIssue[] = [];

    if (/\bcant\b/i.test(plain)) {
      issues.push({
        id: 'CONTRACTION_APOSTROPHE',
        type: 'spelling',
        message: 'Possible missing apostrophe',
        example: `"cant" -> "can't"`,
      });
    }

    if (/\bim\b/i.test(plain)) {
      issues.push({
        id: 'IM_APOSTROPHE',
        type: 'spelling',
        message: 'Possible missing apostrophe',
        example: `"im" -> "I'm"`,
      });
    }

    if (/  /.test(plain)) {
      issues.push({
        id: 'DOUBLE_SPACES',
        type: 'style',
        message: 'Double spaces detected',
        example: 'Remove extra spaces for cleaner writing.',
      });
    }

    const sentences = plain.split(/[.!?]+/).map((part) => part.trim()).filter((part) => part.length > 5);
    const sentencesWithoutCapital = sentences.filter((part) => /^[a-z]/.test(part));
    if (sentences.length > 0 && sentencesWithoutCapital.length >= Math.ceil(sentences.length / 2)) {
      issues.push({
        id: 'LOWERCASE_SENTENCES',
        type: 'style',
        message: 'Some sentences start without a capital letter',
        example: 'Start each sentence with a capital letter.',
      });
    }

    const lastChar = plain.charAt(plain.length - 1);
    if (plain.length > 20 && lastChar && !'.!?'.includes(lastChar)) {
      issues.push({
        id: 'MISSING_END_PUNCTUATION',
        type: 'punctuation',
        message: 'Text does not end with punctuation',
        example: 'Finish the body with a period, question mark, or exclamation mark.',
      });
    }

    return {
      issues,
      score: Math.max(0, 100 - issues.length * 10),
    };
  }
}
