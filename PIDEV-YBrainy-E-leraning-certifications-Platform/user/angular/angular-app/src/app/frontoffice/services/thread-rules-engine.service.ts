import { Injectable } from '@angular/core';

export type RuleSeverity = 'blocking' | 'warning' | 'tip';

export interface RuleViolation {
  id: string;
  severity: RuleSeverity;
  message: string;
  detail?: string;
}

export interface RulesResult {
  violations: RuleViolation[];
  blocking: RuleViolation[];
  warnings: RuleViolation[];
  tips: RuleViolation[];
  canSubmit: boolean;
}

@Injectable({ providedIn: 'root' })
export class ThreadRulesEngineService {
  analyze(title: string, body: string): RulesResult {
    const cleanTitle = title.trim();
    const plainBody = body.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    const violations: RuleViolation[] = [];

    if (cleanTitle.length < 10) {
      violations.push({
        id: 'TITLE_TOO_SHORT',
        severity: 'blocking',
        message: 'Title is too short',
        detail: `Use at least 10 characters. Current length: ${cleanTitle.length}.`,
      });
    }

    if (plainBody.length < 30) {
      violations.push({
        id: 'BODY_TOO_SHORT',
        severity: 'blocking',
        message: 'Body needs more detail',
        detail: `Use at least 30 characters. Current length: ${plainBody.length}.`,
      });
    }

    if (cleanTitle.length >= 5 && cleanTitle === cleanTitle.toUpperCase() && /[A-Z]/.test(cleanTitle)) {
      violations.push({
        id: 'TITLE_ALL_CAPS',
        severity: 'blocking',
        message: 'Avoid all-caps titles',
        detail: 'Titles written entirely in uppercase are usually treated as low quality.',
      });
    }

    if (/^[\d\s\W]+$/.test(cleanTitle)) {
      violations.push({
        id: 'TITLE_INVALID',
        severity: 'blocking',
        message: 'Title needs real words',
        detail: 'Please use a meaningful title instead of only digits or symbols.',
      });
    }

    if (cleanTitle.length > 120) {
      violations.push({
        id: 'TITLE_TOO_LONG',
        severity: 'warning',
        message: 'Title is very long',
        detail: `Try to stay under 120 characters. Current length: ${cleanTitle.length}.`,
      });
    }

    if (plainBody.length >= 30 && plainBody.length < 100) {
      violations.push({
        id: 'BODY_BRIEF',
        severity: 'warning',
        message: 'Body could use more context',
        detail: 'A little more detail makes it easier for the community to help.',
      });
    }

    if (!plainBody.includes('?') && plainBody.length > 50) {
      violations.push({
        id: 'NO_CLEAR_QUESTION',
        severity: 'warning',
        message: 'Question is not obvious',
        detail: 'If you are asking for help, make the question explicit.',
      });
    }

    const titleWords = cleanTitle.toLowerCase().split(/\s+/).filter((word) => word.length > 2);
    const uniqueWords = new Set(titleWords);
    if (titleWords.length > 3 && uniqueWords.size < titleWords.length * 0.6) {
      violations.push({
        id: 'REPEATED_TITLE_WORDS',
        severity: 'warning',
        message: 'Title repeats too many words',
        detail: 'Try to make the title more specific and less repetitive.',
      });
    }

    const alphabeticRatio = (plainBody.match(/[a-z]/gi) ?? []).length / (plainBody.length || 1);
    if (plainBody.length > 100 && alphabeticRatio < 0.3) {
      violations.push({
        id: 'BODY_TOO_CODE_HEAVY',
        severity: 'warning',
        message: 'Body looks mostly like code',
        detail: 'Add context about the problem before or after the code sample.',
      });
    }

    if (plainBody.length >= 100 && plainBody.length < 200) {
      violations.push({
        id: 'BODY_CAN_EXPAND',
        severity: 'tip',
        message: 'A bit more detail could help',
        detail: 'Examples, expected results, and what you tried will improve replies.',
      });
    }

    if (/^(how|why|what|when|where|which|who|can|does|is|are)\b/i.test(cleanTitle) && !cleanTitle.endsWith('?')) {
      violations.push({
        id: 'QUESTION_MARK_MISSING',
        severity: 'tip',
        message: 'Title looks like a question',
        detail: 'Consider ending the title with a question mark.',
      });
    }

    const blocking = violations.filter((item) => item.severity === 'blocking');
    const warnings = violations.filter((item) => item.severity === 'warning');
    const tips = violations.filter((item) => item.severity === 'tip');

    return {
      violations,
      blocking,
      warnings,
      tips,
      canSubmit: blocking.length === 0,
    };
  }
}
