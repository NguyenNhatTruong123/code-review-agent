# Coding Rules

Tài liệu này là chuẩn chung cho code mới và code được sửa trong dự án. Khi quy ước trong module đã tồn tại và không mâu thuẫn với các yêu cầu dưới đây, hãy giữ phong cách của module.

## 1. Đọc và giữ cấu trúc dự án

- Trước khi thay đổi, đọc README, cấu hình build và code liên quan để hiểu framework, phiên bản runtime, naming và ranh giới module.
- Giữ trách nhiệm module hiện có: `code-review-web` cho giao diện React, `code-review-api` cho API/nghiệp vụ, `code-review-ai` cho phân tích AI.
- Không thêm framework, dependency, layer hoặc abstraction chỉ để xử lý một trường hợp đơn giản.
- Không thay đổi file/format/libraries không liên quan tới yêu cầu.
- Không đổi public API hoặc behavior hiện có khi chưa cập nhật các nơi sử dụng và tài liệu tương ứng.

## 2. Rõ ràng và dễ bảo trì

- Ưu tiên cách viết đơn giản, tên biến/hàm/class mô tả mục đích; tránh viết tắt khó hiểu và tên chung chung như `data`, `manager`, `helper` khi có thể gọi đúng vai trò.
- Mỗi hàm nên làm một việc rõ ràng; tách hàm khi việc đó giúp hiểu hoặc kiểm chứng logic.
- Tránh lồng điều kiện sâu; dùng guard clause khi giúp luồng xử lý rõ hơn.
- Tránh duplicate logic. Chỉ tạo abstraction dùng chung khi có nhiều caller hoặc một ranh giới nghiệp vụ thực sự.
- Không để lại dead code, import thừa, TODO không giải thích, debug output hoặc commented-out code.
- Dùng constants/configuration cho giá trị dùng lặp hoặc thay đổi theo môi trường; không hard-code URL, secret, quota hay giới hạn vận hành trong logic.

## 3. Kiểu dữ liệu và xử lý lỗi

- Dùng kiểu dữ liệu cụ thể; tránh `any`, raw collections hoặc cast không cần thiết. Với dữ liệu ngoài hệ thống, parse/validate trước khi dùng.
- Phân biệt trạng thái hợp lệ, giá trị thiếu và lỗi; không dùng `null`, empty string hoặc sentinel tùy tiện thay cho trạng thái.
- Xử lý lỗi ở nơi có đủ ngữ cảnh để phản ứng; giữ nguyên cause khi wrap exception.
- Không bắt lỗi rồi bỏ qua, không trả kết quả giả để che lỗi, không dùng exception như luồng điều khiển thông thường.
- Log lỗi với correlation/review ID và mã lỗi; tránh log source, prompt đầy đủ, token, secret hoặc dữ liệu cá nhân.
- Thông báo cho người dùng phải có thể hành động; chi tiết kỹ thuật nhạy cảm chỉ lưu ở log được bảo vệ.

## 4. Quy tắc theo module

### Frontend (`code-review-web`)

- Dùng component có trách nhiệm rõ; tách UI, gọi API và xử lý nghiệp vụ khi component trở nên khó đọc.
- Tách API client khỏi component; xử lý loading, empty, success và error state.
- Không lưu secret/token AI trong frontend hoặc bundle; không giả định UI validation thay thế server validation.
- Render code, tên file, rule text và finding như text đã escape; không đưa input người dùng vào HTML thô.
- Giữ accessibility cơ bản: label cho input, thao tác được bằng bàn phím, focus dễ nhận biết và lỗi gắn với field.
- Dùng key ổn định cho danh sách; tránh mutation trực tiếp state và effect có thể tạo request lặp ngoài ý muốn.

### API/backend (`code-review-api`)

- Tách controller (HTTP mapping), service (use case), repository/storage (persistence) theo cấu trúc dự án; controller không chứa quy tắc nghiệp vụ phức tạp.
- Dùng request/response DTO; không trả trực tiếp entity lưu trữ hoặc để client gán field nội bộ như owner/status/role.
- Thực hiện transaction khi nhiều thay đổi cần cùng thành công hoặc cùng rollback.
- Giữ trạng thái review và chuyển trạng thái qua logic tập trung; không cập nhật status tùy tiện ở nhiều nơi.
- Gọi GitHub/AI provider qua adapter/service có timeout và xử lý lỗi upstream.
- Dùng cấu hình theo môi trường; không đưa giá trị nhạy cảm vào source, resource public hoặc ví dụ thật.

### AI module (`code-review-ai`)

- Dùng input/output có schema và validate ở biên module.
- Chỉ đánh giá rules trong snapshot được cấp; mọi finding phải gắn với rule đó.
- Giữ line/path provenance khi chunk hoặc chuẩn hóa source code; không bịa vị trí.
- Coi code và rule custom là input không đáng tin; không thực thi chúng như lệnh/mã.
- Khi output không hợp lệ hoặc thiếu bằng chứng, trả lỗi/warning có cấu trúc thay vì finding phỏng đoán.

## 5. Tên và định dạng

- Tuân theo naming convention hiện có của ngôn ngữ/framework; không trộn nhiều quy ước trong cùng module.
- Dùng UTF-8 và giữ line ending/format hiện hữu của file.
- Xóa import/biến không dùng và giữ thứ tự import nhất quán với formatter/linter của dự án.
- Dùng format tự động của repository nếu đã cấu hình. Không tạo formatter/linter mới nếu yêu cầu không cần.
- Tên API field và dữ liệu lưu nên nhất quán về casing; chuyển đổi casing tại boundary thay vì dùng lẫn lộn.

## 6. Kiểm chứng thay đổi

- Thay đổi phải có cách kiểm chứng phù hợp: build/typecheck/lint hoặc kiểm tra thủ công được nêu rõ nếu không có lệnh phù hợp.
- Khi thêm behavior, bổ sung kiểm chứng cho luồng chính và lỗi quan trọng; không chỉ kiểm tra happy path.
- Không ghi nhận một lệnh kiểm tra là thành công nếu lệnh chưa chạy hoặc bị lỗi.
- Giữ thay đổi dễ review: phạm vi nhỏ, diff tập trung, không format lại toàn repository không cần thiết.

## 7. Quy tắc đặc biệt cho code review agent

- Không để AI tạo finding không liên kết với rule đã chọn.
- Không coi severity là confidence; hai khái niệm phải được lưu/hiển thị riêng nếu cả hai được dùng.
- Không ghi đè finding gốc khi người dùng gửi feedback; feedback là dữ liệu riêng.
- Review repository phải gắn với commit SHA cố định và rule snapshot cố định.
- Không âm thầm bỏ file, cắt code hoặc bỏ rule do giới hạn; trả thống kê/warning rõ ràng.

## 8. Quy tắc viết comment và Javadoc

- Comment phải giải thích mục đích, lý do hoặc invariant khó suy ra từ code; không mô tả lại từng dòng code.
- BẮT BUỘC: class, interface, record, public constructor, public method và public API phải có Javadoc dạng block `/** ... */`. Với method/constructor/record có tham số, phải có `@param` giải thích từng tham số; method có kết quả phải có `@return`; method có exception quan trọng hoặc khai báo `throws` phải có `@throws`. Không được coi audit là đạt nếu chỉ có một câu mô tả hoặc còn thiếu tag cần thiết.
- Method private chỉ cần comment khi có logic không hiển nhiên, workaround, thứ tự xử lý bắt buộc hoặc ràng buộc từ hệ thống bên ngoài.
- Comment phải được cập nhật hoặc xóa khi behavior/code thay đổi; không để comment sai, lỗi thời hoặc mâu thuẫn với implementation.
- Không dùng comment để che code chết, code bị vô hiệu hóa, TODO không có context hoặc workaround không có lý do.
- Comment về security, transaction, retry, timeout, concurrency, cache, consistency hoặc external API phải ghi rõ ràng lý do và ràng buộc liên quan.
- Javadoc không được hứa hẹn behavior mà implementation không đảm bảo; không đưa secret, token, prompt nội bộ hoặc dữ liệu nhạy cảm vào comment.
- Giữ comment ngắn, cụ thể, đúng ngôn ngữ và format của file; Javadoc public Java phải mô tả trách nhiệm và contract bằng `@param`, `@return`, `@throws` khi áp dụng, còn comment inline chỉ định hướng cho block logic phức tạp.
- Khi chỉ thêm comment, không được thay đổi business logic, public API, format không liên quan hoặc thứ tự xử lý.

