export type StorageUsage = {
  usedBytes: number;
  quotaBytes: number;
  usedRatio: number;
  warning: boolean;
};

export type UserInfo = {
  id: string;
  email: string;
  name: string;
  provider: string;
  image?: string | null;
  devMode: boolean;
};

export type MediaSummary = {
  id: string;
  fileName: string;
  mimeType: string;
  byteSize: number;
  width?: number;
  height?: number;
  caption?: string;
  createdAt: string;
  contentUrl: string;
};

export type VersionDetail = {
  id: string;
  versionNumber: number;
  body: string;
  negativePrompt?: string;
  parametersJson?: string;
  changeSummary?: string;
  createdAt: string;
  createdById: string;
};

export type VersionSummary = {
  id: string;
  versionNumber: number;
  changeSummary?: string;
  createdAt: string;
  createdById: string;
};

export type PromptSummary = {
  id: string;
  title: string;
  description?: string;
  status: string;
  mediaTypeFocus: string;
  tags: string[];
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
  lastModifiedAt: string;
  mediaCount: number;
  thumbnailMediaId?: string | null;
};

export type PromptDetail = {
  id: string;
  title: string;
  description?: string;
  status: string;
  mediaTypeFocus: string;
  tags: string[];
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
  lastModifiedAt: string;
  latestVersion: VersionDetail;
  media: MediaSummary[];
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};
