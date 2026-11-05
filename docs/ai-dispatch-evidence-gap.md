# AI Dispatch — khảo sát nguồn bằng chứng và điểm thiếu

Ngày khảo sát: 2026-11-04. Chỉ đọc source và migration; chưa đọc dữ liệu nghiệp vụ, chưa chạy migration.

## 1. Luồng hiện tại và nguyên nhân `evidence_unavailable`

`POST /api/ai/dispatch/runs` vào `AiDispatchController.runDispatch`, lấy tenant từ `TenantContext`, rồi gọi `AiDispatchService.runDispatch`. Service kiểm tra feature flag, chế độ Human-in-the-Loop, load tồn tại và gap BLOCKING. Sau đó service hiện **không truy vấn ứng viên, không gọi LLM, không sinh decision**; nó gán cứng `rankingSource=evidence_unavailable`, tạo session `COMPLETED` với `decisionCount=0`, ghi `AiRun` trạng thái `FALLBACK`, rồi trả danh sách suggestions rỗng.

Đây là nguyên nhân trực tiếp khiến AI Dispatch hiện không thể đề xuất. Giữ nhánh này làm fallback khi một hoặc nhiều bằng chứng bắt buộc thiếu.

Các bảng và lưu trữ liên quan đã có trong source:

| Thành phần | Nguồn schema/source | Vai trò hiện tại |
|---|---|---|
| `ai_dispatch_sessions` | baseline V1; tenant mapping bổ sung ở V44 | Lưu phiên và summary |
| `ai_dispatch_decisions` | baseline V1; tenant mapping bổ sung ở V44 | Lưu gợi ý; `tool_input`/`tool_output` là TEXT để lưu bằng chứng/đầu ra có kiểm soát |
| `ai_load_gaps`, `ai_load_gap_history`, `ai_runs` | V44 | Gap và nhật ký chạy |
| `optimization_*` | V30–V33 | Policy/run/candidate/acceptance bất biến; không phải cùng flow AI Dispatch |
| HOS | baseline V1: `driver_hos_statuses`, `hos_logs`, `hos_violations` | Snapshot phút còn lại, log và vi phạm ELD/HOS |
| Rate/cost | V21+ `customer_rate_rule_versions`; `driver_pay_policies`; V9+ `shipment_costs` | Có dữ liệu giá khách hàng/lương/chi phí phát sinh; không phải mọi bảng đều là chi phí vận hành dự báo |

AI Dispatch approval hiện dùng `LoadService.assignTruck(loadId, truckId)`; chưa có hợp đồng gán rơ-moóc. Không đổi cơ chế duyệt trong lượt khảo sát.

## 2. Bằng chứng theo loại

| Loại | Đã có trong DB/service | Còn thiếu / không được suy diễn | Cách xử lý an toàn |
|---|---|---|---|
| Định tuyến | Load có tọa độ pickup/delivery; Truck có `current_location_latitude/longitude`; `DriverOperationalStatus` có vị trí và thời điểm; frontend gọi OSRM qua `useRouteEstimate` và `VITE_ROUTING_API_URL`. Backend hiện không dùng tuyến này cho AI Dispatch. Optimization có `OptimizationInputProvider` nhưng adapter hiện cần endpoint HTTPS/token đã cấu hình. | Backend routing adapter dùng chung/chưa tồn tại. `Load.distance` không chứng minh là road miles và không được dùng thay tuyến. Nếu thiếu tọa độ hiện tại xe/tài xế, deadhead không thể tính. | Gọi routing API từ backend cho vị trí hiện tại → pickup → delivery; lưu geometry summary, distance, duration, source URL/provider key (không token), retrieval time. Khi endpoint/time-out/route không có thì trả thiếu tuyến. |
| ETA | Load có `requested_pickup_date` và `requested_delivery_date`; Trip stops có appointment start/end. Routing response có thể cung cấp duration. | Backend chưa nối thời gian chạy cho AI. Chưa thấy thời gian bốc/dỡ chuẩn có cấu hình; không có cơ sở cộng waiting/loading nếu DB không có. Không được đổi delivery deadline thành ETA. | ETA được tính theo `retrievedAt + routedDuration` và chỉ cộng thời lượng bốc/dỡ có record rõ ràng. Không có giờ xuất phát/lịch phù hợp thì báo thiếu thời điểm/appointment. |
| HOS | `driver_hos_statuses` có `driving_minutes_remaining`, `on_duty_minutes_remaining`, `cycle_minutes_remaining`, `time_until_break_required`, `is_in_violation`, `last_updated_at`; `hos_logs` và `hos_violations` có lịch sử. Optimization có port `HosFeasibilityService`, nhưng implementation hiện là trusted HTTP adapter. | Chưa có HOS simulation nội bộ được nối với dispatch. Snapshot không tự chứng minh còn hợp lệ theo luật/thời gian; không được tự đặt ngưỡng freshness hoặc giả định thời gian reset. | Dùng đúng snapshot ELD và timestamps; cấu hình freshness/rule set phải có nguồn được duyệt. Thiếu snapshot, stale theo policy, đang vi phạm, hoặc không thể mô phỏng đủ route thì loại ứng viên và ghi rõ driver/field. |
| Chi phí | `DriverPayPolicy` có hourly/per-mile/per-load rates; `shipment_costs` giữ chi phí thực tế; `customer_rate_rule_versions` giữ fuel surcharge/customer rating input. | Chưa thấy rate tiêu hao nhiên liệu/MPG hoặc chi phí vận hành theo xe. Giá fuel surcharge khách hàng không phải giá nhiên liệu nội bộ. `Load.deliveryCostAmount` là giá load, không phải cost. Không có `trailers` entity/table; `containers` là container hàng hóa, không phải rơ-moóc. | Không tính cost bằng giá khách hàng hay `Load.distance`. Muốn có fuel cost cần nguồn fuel price nội bộ và tiêu hao xe; dùng driver pay policy chỉ khi policy hiệu lực và cách tính pay đã rõ. Rơ-moóc không thể gán/duyệt nếu chưa có domain/schema/API. |

## 3. Optimization và HOS integration hiện có

`OptimizationApplicationService.create` là engine thật để kiểm tra cứng và chấm điểm xác định, nhưng request yêu cầu `policyId`, target gắn `tripId`/rating snapshot/pickup stop, driver/truck IDs và source selections cụ thể. `OptimizationPolicyService` không có policy mặc định/list resolver; migration không seed policy. Dynamic route/availability và HOS đến từ trusted HTTPS providers theo context; cost forecast cần qualified inputs. Tự dựng các ID này hoặc dùng load `distance`/revenue làm proxy sẽ vi phạm nguyên tắc bằng chứng. Vì vậy AI Dispatch chưa thể gọi engine đó một cách an toàn nếu chưa có explicit tenant mapping/policy/source config.

## 4. LLM providers và cấu hình

`LlmClient` và `PromptBuilder` tồn tại. `DeterministicFakeLlmProvider` thuộc profile `test`/`dev`, trả fixture và báo usage giả; không được dùng để xếp hạng production. Profile production có `UnconfiguredLlmClient`, luôn ném `AI_LLM_NOT_CONFIGURED`. Không tìm thấy adapter runtime cho Claude/Anthropic, DeepSeek, GLM hoặc Gemini.

`AiDispatchProperties` bind cấu hình `ai.dispatch.*` gồm provider map, type, base URL, model, API key; `toString()` redacts credential. Cấu hình typed này chưa được nối vào service/provider router. API key không được đọc từ người dùng trong khảo sát và không ghi ra tài liệu. Khi thiếu key/provider, deterministic ranking vẫn có thể trả kết quả hợp lệ nếu evidence đủ, với `llm_unavailable`; LLM không được quyết định feasibility hay sửa số liệu.

## 5. Rủi ro và ranh giới triển khai

- `AiDispatchToolRegistry` là implementation cũ, có code cũ không phù hợp để làm nguồn bằng chứng; không dùng làm pipeline mới.
- Tất cả reads cần tenant-bound và giới hạn số ứng viên; decision phải chụp snapshot nguồn trong `tool_input`/`tool_output` để audit.
- Optimization service/HOS port có nhiều callers (`OptimizationApplicationService` direct upstream risk LOW; `HosFeasibilityService` upstream risk MEDIUM). Không sửa chúng để tránh ảnh hưởng luồng tối ưu hóa độc lập.
- Không thấy entity/table/API cho rơ-moóc; không được hiển thị `Container` như rơ-moóc.
- Không có API key là không chặn deterministic fallback. Dữ liệu cost/trailer hoặc policy chưa có là thiếu bằng chứng; không tạo gợi ý giả để vượt qua.
- Chưa xác minh Registry/tenant DB runtime identity và chưa đọc data trong DB dev. Không chạy migration ở bước khảo sát.

## 6. Triển khai sau khảo sát

Backend có bộ đánh giá xác định trong `ai.dispatch.evidence.DispatchEvidenceService`:

- Chỉ lấy xe `AVAILABLE`, có tài xế chính; load hazmat chỉ nhận xe có placard. Tài xế phải `ACTIVE`.
- Gọi OSRM-compatible Routing API qua `AI_DISPATCH_ROUTING_BASE_URL`; không dùng `Load.distance` làm road distance.
- Tính quãng chạy rỗng, tổng quãng tuyến từ hai route leg; ETA = thời điểm muộn hơn giữa hiện tại và pickup appointment + thời gian chạy từ routing response.
- Đọc `driver_hos_statuses`, kiểm tra tuổi snapshot theo `AI_DISPATCH_HOS_MAX_AGE`, vi phạm, driving/on-duty/cycle minutes và thời gian tới kỳ nghỉ.
- Chỉ dự toán lương theo policy có hiệu lực `PER_MILE` với basis planned-all-miles hoặc `PER_LOAD`. Không dùng actual miles, hourly, flat, customer rate hay fuel surcharge làm giá lương/chi phí.
- Điểm xếp hạng xác định: 40% hiệu dụng quãng đường nhỏ nhất/từng ứng viên + 30% thời gian chạy + 30% tiền lương cùng currency. Điểm là thứ hạng tương đối trong tập ứng viên, không phải xác suất thành công.
- Snapshot evidence và kết quả rank được ghi trong `ai_dispatch_decisions.tool_input/tool_output`; session/decision dùng bảng sẵn có. Không tạo migration.
- Khi không có ứng viên, summary trả các thiếu hụt theo load/tài xế/xe và upsert warning `DISPATCH_EVIDENCE_MISSING` trong `ai_load_gaps`. Khi có ứng viên nhưng thiếu fuel profile, đề xuất nêu rõ chi phí nhiên liệu chưa tính và warning cũng được lưu.
- LLM hiện không được gọi để xếp hạng; production chỉ có `UnconfiguredLlmClient`, dev/test có fake. Response proposal đặt `resultCode=LLM_UNAVAILABLE`; deterministic evidence vẫn có thể sinh proposal. Không có adapter thật cho Claude/DeepSeek/GLM/Gemini trong source.
- Dữ liệu quyết định được khôi phục khi đọc lại qua `toSuggestionView`; decision cũ tiếp tục được đánh dấu bằng chứng chưa lưu.

## 7. Khoảng trống còn lại / điều kiện chạy ở dev

- Chưa có cấu hình backend routing endpoint. Cần đặt `AI_DISPATCH_ROUTING_BASE_URL` tới OSRM-compatible endpoint reachable từ backend. Không mặc định gọi dịch vụ public.
- Chưa có `AI_DISPATCH_HOS_MAX_AGE` (ISO-8601 duration, ví dụ cấu hình theo chính sách vận hành đã được duyệt); nếu thiếu, builder từ chối xếp hạng.
- Không có MPG/fuel economy theo xe hoặc giá nhiên liệu nội bộ phù hợp. Hiện điểm chỉ dùng lương driver có policy hỗ trợ, ETA và deadhead; không phát số fuel cost. Muốn có cost đầy đủ cần nguồn MPG + fuel price/mi hoặc API/table vận hành có owner rõ.
- Snapshot HOS hiện tại không dự báo được HOS tại pickup trong tương lai. Builder từ chối dùng snapshot cũ hơn scheduled pickup; cần dự báo HOS/dispatch calendar đã xác thực để hỗ trợ gán trước cho load tương lai.
- Nếu tuyến dài hơn thời gian tới kỳ nghỉ bắt buộc, không có dữ liệu thời lượng nghỉ và điểm dừng để dự báo ETA sau nghỉ nên ứng viên bị loại.
- Chi phí ở nhiều currency không được so sánh nếu không có FX source.
- Không có model/API Trailer; feature hiện chỉ đề xuất truck + driver. Hazmat driver certification chưa được liên kết vào bộ kiểm tra, nên nghiệp vụ hazmat cần bổ sung evidence driver certification trước khi đề xuất; truck placard check hiện chưa đủ để khẳng định tài xế đủ chuẩn.
- Load hiện không có nguồn thời lượng bốc/dỡ chuẩn dùng chung; ETA hiện là thời gian đến theo route sau pickup start, không cộng service/waiting time.
- Chưa chạy migration; không cần schema mới cho phần hiện tại. Chưa được xác minh data dev hoặc gọi thử API có xác thực tại thời điểm ghi phần này.

## 8. Kết luận

`evidence_unavailable` nay chỉ được trả khi không có candidate nào vượt qua bằng chứng bắt buộc; response/session lưu summary tiếng Việt và warning gap chi tiết. Khi route + HOS + ETA + policy lương `PLANNED_ALL_MILES`/`PER_LOAD` đủ, backend có thể tạo/lưu đề xuất xác định, không cần LLM. Tuy vậy, đầy đủ chi phí nhiên liệu, trailer, chứng nhận hazmat, HOS forecast cho pickup tương lai và LLM thật vẫn chưa được triển khai vì repository thiếu các nguồn tương ứng. Không thể báo cáo toàn bộ AI Dispatch end-to-end/production-ready cho tới khi những nguồn này được cấu hình hoặc triển khai có chủ sở hữu.
