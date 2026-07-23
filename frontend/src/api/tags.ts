import { apiGet, apiPost } from "./client";

export interface Tag {
  id: number;
  name: string;
}

export function getTags(): Promise<Tag[]> {
  return apiGet<Tag[]>("/api/tags");
}

export function createTag(name: string): Promise<Tag> {
  return apiPost<Tag>("/api/tags", { name });
}
