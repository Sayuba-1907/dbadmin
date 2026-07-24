import { apiGet, apiPost } from "./client";

/** Backend'in TagResponse DTO'suyla ayni sekil. */
export interface Tag {
  id: number;
  name: string;
}

/** TagController'daki iki endpoint'in (list/create) frontend karsiligi. */
export function getTags(): Promise<Tag[]> {
  return apiGet<Tag[]>("/api/tags");
}

export function createTag(name: string): Promise<Tag> {
  return apiPost<Tag>("/api/tags", { name });
}
