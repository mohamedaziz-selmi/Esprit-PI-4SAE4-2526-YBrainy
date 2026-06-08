import { jsPDF } from 'jspdf';
import type { CvSkeletonId } from '../data/cv-skeleton.options';
import { CV_SKELETON_OPTIONS } from '../data/cv-skeleton.options';

type Rgb = [number, number, number];

interface PdfTheme {
  id: CvSkeletonId;
  /** Bandeau superieur pleine largeur */
  headerRgb: Rgb;
  headerHeightPt: number;
  /** Sous-titre / etiquette modele */
  tagRgb: Rgb;
  /** Titres de sections [SECTION] */
  accentRgb: Rgb;
  /** Corps de texte */
  bodyRgb: Rgb;
  /** Texte sur bandeau */
  headerTitleRgb: Rgb;
  /** Barre laterale (skills-first) */
  sidebarRgb?: Rgb;
  sidebarWidthPt?: number;
  /** Trait vertical gauche pour sections (academic) */
  sectionBarRgb?: Rgb;
  sectionBarWidthPt?: number;
}

const THEMES: Record<CvSkeletonId, PdfTheme> = {
  classic: {
    id: 'classic',
    headerRgb: [67, 56, 202],
    headerHeightPt: 78,
    tagRgb: [196, 181, 253],
    accentRgb: [180, 83, 9],
    bodyRgb: [30, 41, 59],
    headerTitleRgb: [255, 255, 255],
  },
  'skills-first': {
    id: 'skills-first',
    headerRgb: [240, 253, 250],
    headerHeightPt: 0,
    tagRgb: [13, 148, 136],
    accentRgb: [15, 118, 110],
    bodyRgb: [30, 41, 59],
    headerTitleRgb: [15, 23, 42],
    sidebarRgb: [13, 148, 136],
    sidebarWidthPt: 86,
  },
  compact: {
    id: 'compact',
    headerRgb: [249, 115, 22],
    headerHeightPt: 88,
    tagRgb: [255, 237, 213],
    accentRgb: [194, 65, 12],
    bodyRgb: [30, 41, 59],
    headerTitleRgb: [255, 255, 255],
  },
  academic: {
    id: 'academic',
    headerRgb: [196, 181, 253],
    headerHeightPt: 72,
    tagRgb: [91, 33, 182],
    accentRgb: [91, 33, 182],
    bodyRgb: [30, 41, 59],
    headerTitleRgb: [49, 46, 129],
    sectionBarRgb: [52, 211, 153],
    sectionBarWidthPt: 4,
  },
};

const SECTION_LINE = /^\[[^\]]+\]\s*$/;

function paletteLabelFor(id: CvSkeletonId): string {
  return CV_SKELETON_OPTIONS.find((o) => o.id === id)?.paletteLabel ?? id;
}

function applyFill(doc: jsPDF, rgb: Rgb): void {
  doc.setFillColor(rgb[0], rgb[1], rgb[2]);
}

function applyTextColor(doc: jsPDF, rgb: Rgb): void {
  doc.setTextColor(rgb[0], rgb[1], rgb[2]);
}

function drawFirstPageChrome(
  doc: jsPDF,
  theme: PdfTheme,
  pageW: number,
  pageH: number,
  title: string,
  subtitle: string
): { contentLeft: number; contentTop: number; contentWidth: number } {
  const sw = theme.sidebarWidthPt ?? 0;

  if (sw > 0 && theme.sidebarRgb) {
    applyFill(doc, theme.sidebarRgb);
    doc.rect(0, 0, sw, pageH, 'F');
    applyFill(doc, [255, 255, 255]);
    doc.rect(sw, 0, pageW - sw, pageH, 'F');
    applyTextColor(doc, theme.headerTitleRgb);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(17);
    doc.text(title, sw + 36, 52);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    applyTextColor(doc, theme.accentRgb);
    doc.text(subtitle, sw + 36, 68);
    return { contentLeft: sw + 36, contentTop: 92, contentWidth: pageW - sw - 72 };
  }

  const hh = theme.headerHeightPt;
  applyFill(doc, theme.headerRgb);
  doc.rect(0, 0, pageW, hh, 'F');
  applyTextColor(doc, theme.headerTitleRgb);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(18);
  doc.text(title, 36, 48);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  applyTextColor(doc, theme.tagRgb);
  doc.text(subtitle, 36, 64);

  return { contentLeft: 36, contentTop: hh + 24, contentWidth: pageW - 72 };
}

function drawContinuationChrome(doc: jsPDF, theme: PdfTheme, pageW: number, pageH: number): {
  contentLeft: number;
  contentTop: number;
  contentWidth: number;
} {
  const sw = theme.sidebarWidthPt ?? 0;
  if (sw > 0 && theme.sidebarRgb) {
    applyFill(doc, theme.sidebarRgb);
    doc.rect(0, 0, 16, pageH, 'F');
    applyFill(doc, [255, 255, 255]);
    doc.rect(16, 0, pageW - 16, pageH, 'F');
    return { contentLeft: 32, contentTop: 48, contentWidth: pageW - 64 };
  }

  const stripH = 36;
  applyFill(doc, theme.headerRgb);
  doc.rect(0, 0, pageW, stripH, 'F');
  return { contentLeft: 36, contentTop: stripH + 20, contentWidth: pageW - 72 };
}

/**
 * PDF A4 avec couleurs et mise en page selon le squelette Canva selectionne.
 */
export function downloadThemedCvPdf(
  filename: string,
  title: string,
  content: string,
  skeletonId: CvSkeletonId
): void {
  const theme = THEMES[skeletonId] ?? THEMES.classic;
  const doc = new jsPDF({ unit: 'pt', format: 'a4', compress: true });
  const pageW = doc.internal.pageSize.getWidth();
  const pageH = doc.internal.pageSize.getHeight();
  const subtitle = `Modele : ${paletteLabelFor(theme.id)}`;

  let { contentLeft, contentTop, contentWidth } = drawFirstPageChrome(
    doc,
    theme,
    pageW,
    pageH,
    title,
    subtitle
  );

  const rawLines = (content || ' ').split(/\r?\n/);
  const lineHeightBody = 13;
  const lineHeightSection = 16;
  let y = contentTop;
  let pageIndex = 0;

  const ensureSpace = (needed: number): void => {
    if (y + needed <= pageH - 44) return;
    doc.addPage();
    pageIndex += 1;
    const chrome = drawContinuationChrome(doc, theme, pageW, pageH);
    contentLeft = chrome.contentLeft;
    contentTop = chrome.contentTop;
    contentWidth = chrome.contentWidth;
    y = contentTop;
  };

  for (const raw of rawLines) {
    const paragraph = raw.length === 0 ? ' ' : raw;
    const wrapped = doc.splitTextToSize(paragraph, contentWidth) as string[];
    const isSection = SECTION_LINE.test(paragraph.trim());

    for (const line of wrapped) {
      const sectionLine = SECTION_LINE.test(line.trim());
      const lh = sectionLine ? lineHeightSection : lineHeightBody;
      ensureSpace(lh);

      if (sectionLine) {
        if (theme.sectionBarRgb && theme.sectionBarWidthPt) {
          applyFill(doc, theme.sectionBarRgb);
          doc.rect(contentLeft - 10, y - 10, theme.sectionBarWidthPt, lh + 4, 'F');
        }
        doc.setFont('helvetica', 'bold');
        doc.setFontSize(11);
        applyTextColor(doc, theme.accentRgb);
      } else {
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(10);
        applyTextColor(doc, theme.bodyRgb);
      }

      doc.text(line, contentLeft, y);
      y += lh;
    }
  }

  doc.save(filename);
}
