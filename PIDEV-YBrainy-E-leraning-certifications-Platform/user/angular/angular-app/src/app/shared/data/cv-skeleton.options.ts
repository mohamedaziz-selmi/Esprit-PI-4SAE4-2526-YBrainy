export type CvSkeletonId = 'classic' | 'skills-first' | 'compact' | 'academic';

export interface CvSkeletonOption {
  id: CvSkeletonId;
  label: string;
  description: string;
  /** Palette type Canva (aperçu visuel uniquement) */
  paletteLabel: string;
  /** Apercu texte (optionnel, aide ou accessibilite) */
  preview: string;
  /** Instructions envoyees au backend pour structurer le CV genere */
  promptStructure: string;
}

export const CV_SKELETON_OPTIONS: CvSkeletonOption[] = [
  {
    id: 'classic',
    label: 'Classique',
    description: 'Resume, experiences puis formation — le plus universel.',
    paletteLabel: 'Indigo & or',
    preview: `[CONTACT]
Nom | Email | Telephone | Ville | LinkedIn

[PROFIL PROFESSIONNEL]
2 a 4 phrases ciblees sur le poste

[EXPERIENCE]
Entreprise — Poste | Periode
• Realisations en puces (chiffres si possible)

[FORMATION]
Diplome | Etablissement | Annee

[COMPETENCES]
Liste par themes

[LANGUES] (optionnel)`,
    promptStructure: `You MUST output the optimized CV in plain text using EXACTLY these section headings in this order (French labels):
[CONTACT]
[PROFIL PROFESSIONNEL]
[EXPERIENCE]
[FORMATION]
[COMPETENCES]
[LANGUES]

Under [CONTACT], one line with: name, email, phone, city, LinkedIn (only if present in source CV).
Under [PROFIL PROFESSIONNEL], 2-4 targeted sentences.
Under [EXPERIENCE], reverse chronological jobs; each job: company — role | dates then bullet achievements.
Under [FORMATION], degrees with institution and year.
Under [COMPETENCES], grouped keywords.
Under [LANGUES], only if known from the CV.
Do not add other top-level sections. Keep ATS-friendly plain text.`,
  },
  {
    id: 'skills-first',
    label: 'Competences d abord',
    description: 'Ideal profils tech : competences visibles en premier.',
    paletteLabel: 'Ocean & menthe',
    preview: `[CONTACT]
...

[STACK & COMPETENCES CLES]
• Domaines techniques prioritaires pour le poste

[EXPERIENCE PROFESSIONNELLE]
...

[FORMATION]
...

[PROJETS / REALISATIONS] (optionnel)`,
    promptStructure: `You MUST output the optimized CV in plain text using EXACTLY these section headings in this order (French labels):
[CONTACT]
[STACK & COMPETENCES CLES]
[EXPERIENCE PROFESSIONNELLE]
[FORMATION]
[PROJETS / REALISATIONS]

Put the strongest technical or professional skills for the job right after contact, as bullet lines grouped by theme.
Then experience reverse chronological, then education, then optional projects (only from source CV).
Do not invent projects. Do not add other top-level sections.`,
  },
  {
    id: 'compact',
    label: 'Une page',
    description: 'Dense et scannable — priorise l essentiel.',
    paletteLabel: 'Corail & graphite',
    preview: `[CONTACT]
Une ligne

[POSITION CIBLEE]
Une phrase

[POINTS FORTS]
3 a 5 puces courtes

[EXPERIENCE]
Format compact : Entreprise | Poste | Annees — 2 puces max par poste

[FORMATION & CERTIFICATIONS]
Une ligne chacune si pertinent`,
    promptStructure: `You MUST output a compact one-page style CV in plain text using EXACTLY these section headings in this order (French labels):
[CONTACT]
[POSITION CIBLEE]
[POINTS FORTS]
[EXPERIENCE]
[FORMATION & CERTIFICATIONS]

Keep lines short; max 2 bullets per job; prioritize relevance to the job description; omit weak or redundant details.
Do not add other top-level sections.`,
  },
  {
    id: 'academic',
    label: 'Academique / Junior',
    description: 'Met en avant formation, stages et associations.',
    paletteLabel: 'Lavande & sauge',
    preview: `[CONTACT]
...

[FORMATION]
Diplomes, specialite, projets majeurs

[EXPERIENCE & STAGES]
...

[ACTIVITES & ENGAGEMENTS]
Associations, benevolat, competitions

[COMPETENCES & OUTILS]
...

[LANGUES]`,
    promptStructure: `You MUST output the optimized CV in plain text using EXACTLY these section headings in this order (French labels):
[CONTACT]
[FORMATION]
[EXPERIENCE & STAGES]
[ACTIVITES & ENGAGEMENTS]
[COMPETENCES & OUTILS]
[LANGUES]

Prioritize education, internships, and student activities when experience is limited (only from source CV).
Do not invent activities. Do not add other top-level sections.`,
  },
];
