# Bộ context cho frontend

Điểm bắt đầu: [Frontend/backend integration context](../frontend-backend-integration-context.md).

| Artifact | Mục đích |
|---|---|
| [api-operation-index.md](api-operation-index.md) | 176 operations: method, URL, query/body và response mode/schema |
| [openapi-backend-remediation.json](openapi-backend-remediation.json) | OpenAPI 3.1, 140 paths và 291 schemas từ verified local JAR |
| [openapi-provenance.json](openapi-provenance.json) | Source/JAR/canonical hashes, phạm vi local và timestamp |
| [contract-notes.json](contract-notes.json) | Nullable metadata, redirect/delete, raw response và auth/pagination exceptions |
| [request-examples.json](request-examples.json) | 20 payload/query mẫu; IDs/hashes/proofs phải thay bằng dữ liệu thật |

Bản container cũ `:8080` chưa được deploy remediation. Đọc readiness/nguồn ở
context trước khi chạy integration. Bộ này không chứa credential, production
secret hay token dùng được. Payload mẫu đã kiểm tra schema cùng explicit
contract notes; chưa phải financial commands đã chạy hay frontend E2E.

File ZIP bàn giao ở `docs/frontend-backend-handoff.zip` giữ cấu trúc `docs/` và
đi kèm các domain contracts/verification tham chiếu. Khi chuyển cho repo/agent
frontend, giải nén rồi đọc context và prompt ở section 16. Những source paths
`src/main/...` được nhắc trong tài liệu thuộc backend repo; frontend source paths
thuộc `logictics-app`.
