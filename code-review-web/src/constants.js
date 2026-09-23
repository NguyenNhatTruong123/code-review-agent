export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

export const SOURCE_LANGUAGES = [
  'JAVA', 'JAVASCRIPT', 'TYPESCRIPT', 'JSX', 'TSX', 'PYTHON', 'GO', 'CSHARP',
  'RUBY', 'PHP', 'RUST', 'KOTLIN', 'SWIFT', 'C', 'CPP', 'VUE', 'SQL',
];

export const EMPTY_RULE = {
  name: '', description: '', category: 'MAINTAINABILITY', severity: 'MEDIUM',
  languages: 'ALL', instruction: '', matchText: '', suggestedFix: '', enabled: true,
};

export const EMPTY_RULE_SET = { name: '', description: '', ruleIds: [], enabled: true };

export const NAVIGATION_ITEMS = [
  ['new', 'New review'],
  ['reviews', 'Reviews'],
  ['rules', 'Rules'],
  ['sets', 'Rule sets'],
];

export const REVIEW_STATUSES = ['QUEUED', 'RUNNING'];
export const PAGE_SIZE = 50;