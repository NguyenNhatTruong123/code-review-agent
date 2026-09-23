# Security Coding Rules

Tài liệu này quy định các yêu cầu bảo mật bắt buộc khi viết hoặc thay đổi code. Áp dụng cho frontend, API, AI module, tích hợp GitHub và lưu trữ.

## 1. Xác thực, phân quyền và dữ liệu sở hữu

- Endpoint riêng tư phải yêu cầu xác thực ở server.
- Mỗi lần đọc/ghi review, finding, rule hoặc rule set phải kiểm tra quyền sở hữu từ principal đã xác thực.
- Không tin `ownerId`, role, tenant, permission hoặc user ID do client gửi.
- Không dựa vào ID khó đoán, ẩn nút UI hoặc kiểm tra frontend để bảo vệ tài nguyên.
- Từ chối mặc định khi thiếu thông tin xác thực hoặc quyền không rõ.
- Không để lỗi authorization tiết lộ nội dung hay xác nhận sự tồn tại của tài nguyên người khác.

## 2. Secrets và cấu hình

- Không commit API key, access token, password, private key, cookie, signing key hoặc credential mẫu có thể dùng thật.
- Lấy secrets từ secret manager hoặc cấu hình môi trường bảo vệ; không nhúng secret trong frontend, file cấu hình public, image/container hoặc log.
- Không trả secrets trong API response, exception, telemetry hay thông báo UI.
- Hỗ trợ thay/thu hồi credential; dùng quyền tối thiểu và credential chỉ đọc cho việc tải public repository khi khả thi.
- Nếu phát hiện secret trong code đang review, không gửi giá trị secret vào log hoặc finding; chỉ cung cấp vị trí/mô tả đã che.

## 3. GitHub URL và SSRF

URL repository là input không đáng tin và được API server truy cập. Bắt buộc:

- Chỉ cho phép scheme HTTPS và host chính xác `github.com` cho thao tác GitHub public repository.
- Parse URL bằng thư viện URL; không kiểm tra bằng prefix/string match đơn giản.
- Chỉ chấp nhận đường dẫn repository đúng dạng owner/repo (cho phép hậu tố `.git` nếu cần); loại bỏ query/fragment không được hỗ trợ.
- Từ chối userinfo/credential trong URL, địa chỉ IP, hostname tương tự như `github.com.attacker.example`, port bất thường và scheme khác.
- Không theo redirect sang host khác; nếu client HTTP tự redirect, kiểm tra lại đích cuối ở mỗi bước hoặc tắt redirect.
- Không cho người dùng nhập URL tùy ý để fetch file, archive, API hoặc callback. Chỉ gọi endpoint GitHub cần thiết qua adapter cố định.
- Áp dụng timeout, giới hạn response size, redirect limit, connection limit và rate limit.
- Không tải submodule hoặc LFS object tùy ý nếu chưa có policy rõ ràng.

## 4. Xử lý source code và upload

- Coi repository, code paste, archive, tên file, comment và rule custom là dữ liệu không đáng tin.
- Không build, execute, import hoặc chạy script từ repository được review.
- Kiểm tra loại file, encoding, kích thước request, số file, tổng dung lượng giải nén, độ sâu thư mục và thời gian xử lý.
- Phòng chống zip slip/path traversal: chuẩn hóa path; từ chối đường dẫn tuyệt đối, `..`, symlink nguy hiểm và ghi file ngoài thư mục tạm riêng.
- Không lưu upload ở web root; dùng thư mục tạm hạn quyền, tên file nội bộ ngẫu nhiên và xóa sau khi xử lý theo retention.
- Chặn hoặc bỏ qua binary và file không hỗ trợ. Giới hạn phải áp dụng server-side, không chỉ ở UI.
- Không âm thầm truncate input; trả trạng thái/warning nêu rõ phạm vi chưa được phân tích.

## 5. Injection, XSS và AI prompt safety

- Dùng parameterized query/ORM binding; không ghép input vào SQL, shell command, expression hoặc template thực thi.
- Không dùng `eval`, shell với input người dùng, dynamic class loading hoặc template rendering không escape.
- Escape dữ liệu theo context khi render HTML, attribute, URL và JavaScript. Tránh HTML injection; nếu buộc render HTML, sanitize bằng thư viện allowlist.
- Coi code/comment/rule text là dữ liệu; prompt phải tách system instruction khỏi source và nói rõ source không có quyền thay đổi luật review.
- Không cấp cho AI agent quyền chạy lệnh, truy cập filesystem tùy ý, mạng tùy ý hoặc gọi tool không cần thiết.
- Output AI phải qua schema validation và allowlist kiểm tra rule ID, severity, path, line range và kích thước trước khi lưu/hiển thị.
- Không dùng AI output làm SQL, HTML, shell command hay code executable mà không có kiểm tra và encode phù hợp.

## 6. Bảo vệ dữ liệu và logging

- Coi source code là dữ liệu nhạy cảm mặc định. Chỉ truyền tới dịch vụ cần thiết cho review và theo cấu hình provider được phê duyệt.
- Dùng TLS cho kết nối; mã hóa dữ liệu nhạy cảm khi lưu theo hạ tầng triển khai.
- Không log raw source, prompt đầy đủ, token, secret, cookie hoặc nội dung finding có thể chứa dữ liệu nhạy cảm.
- Log tối thiểu review ID, stage, error code, duration và metadata cần vận hành; redact dữ liệu trước khi ghi.
- Đặt retention rõ cho source, artifacts, kết quả và log; xóa dữ liệu theo policy và không giữ source vô thời hạn.
- Không lưu source thô nếu chỉ cần hash/path/kết quả; nếu phải lưu tạm, hạn chế quyền đọc và xóa sau khi hết nhu cầu.
- Không gửi telemetry chứa code hoặc dữ liệu cá nhân tới dịch vụ ngoài theo mặc định.

## 7. Web/API và trình duyệt

- Dùng HTTPS; cấu hình cookie `HttpOnly`, `Secure`, `SameSite` phù hợp nếu dùng cookie session.
- Bảo vệ endpoint thay đổi dữ liệu khỏi CSRF nếu xác thực dựa trên cookie.
- Cấu hình CORS theo allowlist origin cần thiết; không bật wildcard cùng credentials.
- Dùng security headers phù hợp, bao gồm CSP khi tương thích với ứng dụng.
- Giới hạn request size, tốc độ, concurrent jobs và chi phí AI theo user hoặc cấu hình hệ thống.
- Không trả stack trace, framework version, query nội bộ, đường dẫn filesystem hoặc thông tin hạ tầng cho client.
- Tránh open redirect; validate URL điều hướng và link ngoài.

## 8. Dependency và cấu hình vận hành

- Dùng dependency cần thiết, phiên bản được quản lý trong build file/lockfile và nguồn tin cậy.
- Không thêm dependency chưa được bảo trì hoặc thư viện trùng chức năng nếu không cần.
- Không tắt TLS certificate validation, security filter, CORS protection hoặc kiểm tra input để làm cho demo chạy.
- Cấu hình debug, verbose error, test credentials và development endpoint không được bật trong production.
- Tách cấu hình môi trường; mặc định production phải fail closed khi thiếu cấu hình bảo mật thiết yếu.

## 9. Xử lý lỗi và audit

- Trả thông báo lỗi tối thiểu cần thiết cho client; ghi chi tiết nội bộ đã redact với correlation ID.
- Không bắt lỗi bảo mật rồi tiếp tục như thành công.
- Ghi audit cho hành động nhạy cảm như tạo/xóa rule, thay đổi rule set, tạo review và truy cập quản trị; không ghi payload code/secret vào audit.
- Bảo vệ audit log khỏi sửa bởi người dùng thông thường; giới hạn quyền đọc và retention phù hợp.
- Retry upstream phải có timeout, giới hạn lần thử và backoff; tránh retry vô hạn hoặc làm lặp thao tác có side effect.

## 10. Checklist trước khi hoàn tất thay đổi

- [ ] Endpoint mới đã có authentication và kiểm tra ownership ở server.
- [ ] Input không thể điều khiển host/đường dẫn/lệnh ngoài allowlist.
- [ ] Source, token, prompt và secret không bị ghi vào log hoặc response.
- [ ] File và response upstream có giới hạn kích thước/thời gian.
- [ ] Nội dung không tin cậy được escape và AI output được validate.
- [ ] Không có credential, debug mode hoặc bảo vệ bị tắt trong thay đổi.
- [ ] Lỗi không làm lộ stack trace hay dữ liệu của người dùng khác.
