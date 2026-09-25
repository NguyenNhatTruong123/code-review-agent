# API Development Requirements

Tài liệu này quy định các yêu cầu cần tuân theo khi thiết kế, viết và thay đổi API của AI Code Review Agent. Áp dụng cho mọi endpoint giữa `code-review-web`, `code-review-api` và `code-review-ai`.

## 1. Nguyên tắc chung

- API phải có hợp đồng rõ ràng, nhất quán và được kiểm tra ở cả phía nhận lẫn phía gửi.
- Backend là nguồn chuẩn cho validation, authorization, trạng thái nghiệp vụ và dữ liệu đã lưu. Không tin dữ liệu do frontend tự xác nhận.
- Frontend chỉ gọi API nghiệp vụ. Không gọi AI provider hoặc chứa credential của provider.
- Giao tiếp qua HTTPS trong môi trường triển khai. Payload trao đổi dùng JSON, trừ khi endpoint upload cần `multipart/form-data`.
- Không thêm endpoint, field hoặc hành vi ngoài yêu cầu sản phẩm nếu không có lý do cụ thể.

## 2. URL và phiên bản

- Đặt endpoint dưới prefix có version, ví dụ `/api/v1/...`.
- Dùng danh từ số nhiều cho collection: `/reviews`, `/rules`, `/rule-sets`.
- Dùng HTTP method theo ý nghĩa: `GET` để đọc, `POST` để tạo/thực hiện hành động, `PUT` để thay toàn bộ tài nguyên, `PATCH` để cập nhật một phần, `DELETE` để xóa.
- Không dùng `GET` để thay đổi dữ liệu.
- Thay đổi phá vỡ tương thích phải tạo version API mới hoặc có kế hoạch migration tương thích.

## 3. Request và validation

- Xác thực mọi input ở server: kiểu dữ liệu, độ dài, định dạng, enum, giới hạn số phần tử và quan hệ giữa các field.
- Từ chối field bắt buộc bị thiếu, giá trị ngoài phạm vi và field không hỗ trợ; không âm thầm sửa hoặc bỏ qua input sai.
- URL GitHub phải được parse và kiểm tra server-side. Chỉ chấp nhận `https://github.com/{owner}/{repo}` (có thể có `.git`); không chấp nhận credential trong URL, host tùy ý, URL private hoặc scheme không an toàn.
- Với code paste hoặc upload, áp dụng giới hạn dung lượng và loại nội dung ở server trước khi lưu/chuyển tiếp.
- Trim và chuẩn hóa dữ liệu text có quy tắc rõ ràng; không sửa nội dung source code trước khi phân tích theo cách làm sai line number.
- Dùng allowlist cho enum như review status, severity, finding source và feedback type.
- Trả lỗi validation có field/path liên quan để client chỉ ra cách sửa.

## 4. Authentication và authorization

- Endpoint chứa dữ liệu riêng tư phải yêu cầu người dùng đã xác thực, trừ health endpoint được cấu hình công khai.
- Kiểm tra quyền trên từng tài nguyên ở server, bao gồm review, finding, rule và rule set. Không dựa vào việc ID khó đoán hoặc kiểm tra ẩn ở UI.
- Người dùng chỉ được đọc/sửa tài nguyên họ sở hữu trong MVP.
- Khi tài nguyên không thuộc người gọi, trả lỗi không làm lộ sự tồn tại hoặc nội dung tài nguyên đó.
- Không nhận `ownerId`, role hoặc quyền truy cập từ body như nguồn xác thực. Lấy danh tính/quyền từ security context đáng tin cậy.

## 5. Response và lỗi

- Response thành công phải có schema nhất quán; dùng HTTP status phù hợp (ví dụ `200`, `201`, `202`, `204`).
- Lỗi dùng cấu trúc ổn định, ví dụ:

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Request không hợp lệ.",
    "details": [{ "field": "repositoryUrl", "reason": "URL không đúng định dạng." }],
    "correlationId": "..."
  }
  ```

- Mã lỗi phải ổn định để client xử lý; nội dung message hướng tới người dùng và không chứa stack trace, SQL, nội bộ hạ tầng, token hoặc source code.
- Dùng `400` cho request sai, `401` khi chưa xác thực, `403` khi không được phép, `404` khi tài nguyên không tồn tại/không thuộc người gọi, `409` khi xung đột trạng thái, `413` khi vượt dung lượng, `429` khi vượt quota/rate limit, và `5xx` cho lỗi server/upstream.
- Không báo thành công khi công việc nền chưa hoàn thành. Trả `202 Accepted` cùng review ID/status khi xử lý bất đồng bộ.

## 6. Quy ước các API nghiệp vụ

- Tạo review repository phải lưu input URL đã chuẩn hóa, ref được yêu cầu, commit SHA thực tế, snapshot của một rule set hoặc các rule được chọn trực tiếp, owner, trạng thái và thời điểm tạo.
- Tạo review paste phải lưu metadata cần thiết, snapshot của một rule set hoặc các rule được chọn trực tiếp, và owner. Hạn chế lưu raw code theo retention policy.
- Request tạo review phải có chính xác một trong hai lựa chọn: `ruleSetId` của rule set enabled thuộc owner, hoặc `ruleIds` gồm một hay nhiều rule enabled thuộc owner. Backend phải từ chối khi cả hai hoặc không lựa chọn nào được gửi.
- Request tạo repository review có thể bỏ qua `filePaths` để review mọi source file được hỗ trợ, hoặc gửi danh sách path duy nhất từ endpoint liệt kê file để chỉ review các file đó. Backend phải giới hạn số path, kiểm tra path an toàn, extension được hỗ trợ, và kiểm tra file tại commit đã được pin.
- Request rerun phải chọn snapshot gốc, một rule set enabled hiện tại, hoặc một hay nhiều rule enabled hiện tại thuộc owner. Lựa chọn hiện tại phải tạo snapshot mới cho rerun.
- Review tạo xong trả ID và trạng thái ban đầu; client lấy tiến độ/kết quả qua endpoint đọc review.
- Findings phải có rule ID/version thuộc snapshot review, severity hợp lệ, evidence, explanation và suggested fix. `filePath`, `lineStart`, `lineEnd` có thể rỗng nếu không xác định chắc chắn.
- API không chấp nhận finding do client tự gửi để trở thành kết quả chính thức.
- Thay đổi rule tạo version mới hoặc giữ snapshot bất biến; không làm đổi findings của review đã chạy.
- Xóa rule/rule set không được phá vỡ dữ liệu lịch sử hoặc snapshot được tham chiếu.
- Endpoint list phải hỗ trợ phân trang khi collection có thể tăng lớn. Filter/sort chỉ cho phép field được định nghĩa rõ.

## 7. Tác vụ bất đồng bộ, retry và tính nhất quán

- Phân tích repository/AI là tác vụ dài: API phải tạo review và trả trạng thái, không giữ kết nối HTTP mở suốt thời gian phân tích.
- Dùng state hợp lệ: `QUEUED`, `RUNNING`, `COMPLETED`, `COMPLETED_WITH_WARNINGS`, `FAILED`, `CANCELLED`. Chỉ cho phép chuyển trạng thái theo luồng nghiệp vụ đã định nghĩa.
- Retry phải có giới hạn, backoff phù hợp và không tạo review/finding trùng. Dùng idempotency key cho thao tác tạo có thể bị client gửi lại do timeout.
- Lỗi từng phần phải được lưu thành warning/error có mã và stage; không che giấu file hoặc rule chưa xử lý.
- Xác thực schema response từ AI module trước khi lưu. Loại bỏ finding có rule ngoài snapshot, enum sai, line ngoài phạm vi hoặc thiếu căn cứ bắt buộc.

## 8. Tài liệu và kiểm chứng hợp đồng

- Với mỗi endpoint mới/thay đổi, cập nhật API schema hoặc tài liệu cùng mã nguồn: method, path, auth, request, response, lỗi và ví dụ tối thiểu.
- DTO/request/response cần tên field rõ, kiểu dữ liệu cụ thể, optional/null được quy định tường minh.
- Khi có OpenAPI trong dự án, giữ schema đồng bộ với implementation; không tạo tài liệu mâu thuẫn với code.
- Mọi endpoint cần có kiểm chứng cho validation, authorization, phản hồi thành công và lỗi chính. Kiểm thử không được dùng dữ liệu production hoặc credential thật.
- Không trả dữ liệu nội bộ thừa; chỉ đưa ra field cần thiết cho chức năng gọi API.

## 9. Rule instruction suggestion API

## 9.1 Repository source-file listing API

### `GET /api/v1/github/source-files?url={repositoryUrl}&ref={ref}`

- Yêu cầu người dùng đã xác thực. `url` là GitHub repository URL public hợp lệ; `ref` là optional và mặc định là default branch.
- Response `200 OK` là danh sách object `{ "path": "src/App.java", "language": "JAVA" }` chỉ bao gồm source file được hỗ trợ và không thuộc thư mục bị loại trừ.
- Endpoint chỉ lấy metadata từ GitHub tree API, không tải source content.
- Trả `400` khi repository, ref, hoặc file tree không hợp lệ/quá lớn; `503` khi GitHub rate limit; và `502` khi GitHub không phản hồi hợp lệ.

## 10. Rule instruction suggestion API

### `POST /api/v1/rules/instruction-suggestions`

- Yêu cầu người dùng đã xác thực và CSRF token hợp lệ.
- Request JSON gồm `name` (1–120 ký tự) và `description` (1–2000 ký tự), đều bắt buộc.
- Response `200 OK` có dạng `{ "instruction": "..." }`. Draft không được lưu và chỉ được áp dụng khi người dùng gửi rule create/update riêng.
- Trả `400` khi thiếu hoặc vượt giới hạn input; `503` khi AI provider chưa cấu hình hoặc request bị ngắt; `504` khi timeout; và `502` khi upstream trả lỗi hoặc output không hợp lệ.
- Credential AI chỉ được dùng tại backend. Rule name, description, và generated text được coi là input không đáng tin và không được log như nội dung đầy đủ.

