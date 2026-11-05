# Ma trận truy vết ban đầu

Các đường dẫn Java tương đối với `src/main/java/com/company/logicstic/`; frontend tương đối với `/home/vumoi/logictics-app/`.

| Module | Màn hình / component | Chức năng | Frontend | API / controller | Service / repository | Entity | Trạng thái |
|---|---|---|---|---|---|---|---|
| Load | LoadCreate, LoadForm | UC001 Tạo | `src/pages/loads/create.tsx`, `src/providers/dataProvider.ts` | POST `/api/loads`, `LoadController.create` | `LoadService.create`, `LoadRepository.saveAndFlush`, các repository quan hệ, `LoadPickupBusinessDateService.capture` | `Load` | Đối chiếu tĩnh; chưa chạy |
| Load | LoadEdit, LoadForm | UC002 Sửa | `src/pages/loads/edit.tsx` | PUT `/api/loads/{id}`, `LoadController.update` | `LoadService.update`, `LoadRepository.findByIdForUpdate` | `Load` | Đối chiếu tĩnh; chưa chạy |
| Load | LoadShow | UC003 Xem | `src/pages/loads/show.tsx`, data provider | GET `/api/loads/{id}`, `LoadController.getById` | `LoadService.getById`, `LoadRepository.findById` | `Load` | Đối chiếu tĩnh; chưa chạy |
| Load | LoadTimelinePanel | UC004 Timeline | `src/features/loads/components/LoadTimelinePanel.tsx` | GET `/api/loads/{id}/timeline`, `LoadTimelineController.getTimeline` | `LoadTimelineService.getTimeline`, `LoadEventRepository.findPageByLoadId` | `Load`, `LoadEvent` | Đối chiếu tĩnh; chưa chạy |

| UC ID | Đặc tả | Use Case Diagram | Activity | Sequence | API | Kiểm chứng |
|---|---|---|---|---|---|---|
| UC001–UC004 | Có, `03-usecase-specifications/UC001-UC004-Load.md` | `UC_Load.puml` | Mỗi UC một sơ đồ | Mỗi UC một sơ đồ | Có | Phân tích tĩnh; chưa nghiệm thu runtime |

## Bổ sung và điều chỉnh

| Module | Màn hình | Use case | Frontend | API / controller | Service / repository | Entity | Trạng thái |
|---|---|---|---|---|---|---|---|
| Load | Điều phối `/tms/dispatch` | UC005 | `src/pages/tms-flow/DispatchPage.tsx`, `src/api/tms-flow/tmsFlow.api.ts` | GET `/api/loads`, `LoadController.search` | `LoadService.search`, `LoadRepository.search` | Load | Phân tích tĩnh |
| Load | Chưa xác minh nút xóa | UC006 | `dataProvider.deleteOne`, capability delete | DELETE `/api/loads/{id}`, `LoadController.delete` | `LoadService.delete`, `LoadRepository` | Load | UI cần xác minh |
| Load | Form có mã nhưng bị tắt | UC007 | `PickupBusinessDateForm`, cờ runtime false | POST `/api/loads/{id}/requested-pickup-business-date` | `LoadPickupBusinessDateService.remediate`, `LoadRepository`, `EmployeeRepository`, JDBC | Load, lịch sử ngày | UI bị tắt |

**Đính chính UC002:** `CORE_VERSIONED_WRITES_RUNTIME_VERIFIED = false` khiến route sửa Load không được mount trong cấu hình hiện tại; sơ đồ UI trước đây mô tả mã có điều kiện, không chứng minh thao tác hiện khả dụng. UC001 chuyển về `/loads`, route này redirect `/tms/dispatch`. Nguồn frontend: `src/components/resources/resourceCapabilities.ts`, `src/router/AppRouter.tsx`.

| Module | Màn hình | UC | Frontend | API / Controller | Service / Repository | Entity | Trạng thái |
|---|---|---|---|---|---|---|---|
| Load / chứng từ | Tab chứng từ Load | UC008 | `LoadDocumentsPanel`, `dataProvider.getList` | GET `/api/documents`, `DocumentController.search` | `DocumentService.search`, `DocumentRepository.search` | Document | Đối chiếu tĩnh; chưa chạy runtime |
