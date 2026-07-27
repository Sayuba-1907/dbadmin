import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

/** Backend'in SchemaResponse DTO'suyla ayni sekil. */
export interface Schema {
  id: number;
  name: string;
}

/** SchemaController'daki endpoint'lerin frontend karsiligi. */
export function getSchemalar(): Promise<Schema[]> {
  return apiGet<Schema[]>("/api/schemalar");
}

export function createSchema(name: string): Promise<Schema> {
  return apiPost<Schema>("/api/schemalar", { name });
}

export function deleteSchema(id: number): Promise<void> {
  return apiDelete(`/api/schemalar/${id}`);
}

export function renameSchema(id: number, name: string): Promise<Schema> {
  return apiPatch<Schema>(`/api/schemalar/${id}`, { name });
}
