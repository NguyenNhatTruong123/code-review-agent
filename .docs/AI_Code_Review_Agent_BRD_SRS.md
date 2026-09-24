# BRD/SRS — AI Code Review Agent

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 |
| Trạng thái | Bản yêu cầu đã tinh gọn, sẵn sàng làm đầu vào thiết kế và generate code |
| Ngày cập nhật | 23/09/2026 |
| Ngôn ngữ | Tiếng Việt |
| Phạm vi repository | `code-review-web`, `code-review-api`, `code-review-ai` |

## 1. Mục tiêu sản phẩm

Người dùng cung cấp URL của một GitHub repository công khai, chọn bộ coding rules, và nhận báo cáo các vị trí vi phạm kèm giải thích cùng gợi ý sửa. Người dùng cũng có thể tự định nghĩa rules và review code được paste trực tiếp.

Sản phẩm hỗ trợ developer phát hiện vấn đề theo quy tắc đã chọn. Sản phẩm không tự sửa, commit hay push code. Người dùng tự quyết định có áp dụng gợi ý hay không.

## 2. Phạm vi phiên bản đầu (MVP)

### Bao gồm

1. Review source code từ URL GitHub public.
2. Chọn nhánh/commit cần review; mặc định dùng nhánh mặc định của repository tại thời điểm submit.
3. Chọn một bộ rules có sẵn hoặc bộ rules do người dùng tạo.
4. Paste trực tiếp một đoạn hoặc một file code để review với bộ rules đã chọn.
5. Hiển thị findings theo file và dòng, có thể lọc theo severity/rule/file.
6. Cho phép tạo, sửa, bật/tắt, xóa rules cá nhân; lưu và quản lý các rule set cá nhân.
7. Lưu lịch sử review và trạng thái xử lý để người dùng mở lại kết quả.

### Không bao gồm trong MVP

- Repository private, GitHub OAuth/App installation, GitLab/Bitbucket.
- Pull request review, webhook, CI/CD quality gate, IDE plugin, SARIF export.
- Tự động áp dụng patch, commit, push hoặc tạo pull request.
- Quét bảo mật/dependency chuyên dụng thay thế SAST hoặc vulnerability scanner.
- Chia sẻ/quản trị rule giữa nhiều tổ chức, workspace hay tenant.

## 3. Người dùng và quyền

MVP có một loại người dùng đã đăng nhập. Người dùng chỉ xem và quản lý review, rules, rule sets thuộc tài khoản của mình. Dữ liệu và API phải được phân quyền theo chủ sở hữu; không cho phép truy cập tài nguyên của người dùng khác.

Nếu dự án chưa có cơ chế đăng nhập, cần triển khai xác thực tối thiểu trước khi lưu dữ liệu cá nhân. Không được coi định danh gửi từ trình duyệt là bằng chứng xác thực.

## 4. Luồng chính

### 4.1 Review GitHub repository

1. Người dùng nhập URL repository dạng `https://github.com/{owner}/{repo}`; có thể nhập nhánh hoặc commit trong trường riêng.
2. Hệ thống xác thực định dạng và xác nhận repository truy cập công khai được.
3. Hệ thống lấy metadata, liệt kê nhánh/commit khả dụng và cho người dùng xác nhận mục tiêu review. Nếu người dùng không chọn, dùng nhánh mặc định.
4. Người dùng chọn rule set và bắt đầu review.
5. Hệ thống ghi nhận commit SHA chính xác trước khi phân tích, tải source code và chỉ xử lý các file source code được hỗ trợ.
6. Hệ thống chạy các rules đã chọn, lưu kết quả và hiển thị trạng thái tiến độ.
7. Người dùng mở finding để xem rule, vị trí, bằng chứng, giải thích và đề xuất sửa.

Nếu nhánh thay đổi sau khi submit, review vẫn gắn với commit SHA đã chụp tại thời điểm bắt đầu. Không dùng code mới nhất để thay thế ngầm input của review.

### 4.2 Review code paste

1. Người dùng paste code, nhập tên file tùy chọn và chọn ngôn ngữ nếu hệ thống không nhận diện được.
2. Người dùng chọn rule set và submit.
3. Kết quả dùng vị trí dòng tính từ dòng đầu tiên của đoạn code được paste.

### 4.3 Quản lý rule

Người dùng tạo rule với tên, mô tả, nhóm, severity, ngôn ngữ áp dụng và chỉ dẫn cụ thể để xác định vi phạm. Rule có trạng thái enabled/disabled. Người dùng có thể tạo rule set bằng cách chọn nhiều rules. Mỗi review lưu snapshot nội dung/version rules đã dùng để sửa rule sau này không đổi kết quả cũ.

Rule do người dùng viết là dữ liệu đầu vào không đáng tin. Rule phải được giới hạn trong chức năng review; không được thực thi như mã lệnh, truy cập mạng, đọc file tùy ý hoặc điều khiển hệ thống.

## 5. Quy tắc xử lý source code

- Chỉ chấp nhận repository public trên host `github.com`; từ chối URL không phải repository GitHub, URL private/không truy cập được, hoặc URL chứa credential.
- Chỉ phân tích file text có phần mở rộng/ngôn ngữ được hệ thống hỗ trợ. Bỏ qua binary, generated/vendor/dependency folders và file bị loại trừ theo cấu hình mặc định; báo số lượng file đã phân tích và bỏ qua.
- Không chạy/build/execute source code lấy từ repository.
- Giới hạn dung lượng repository, số file, dung lượng mỗi file, độ sâu thư mục và thời gian review bằng cấu hình server. Khi vượt giới hạn, thông báo rõ và không âm thầm bỏ qua nội dung.
- Không gửi nội dung source hoặc rule custom tới log. Nếu AI provider bên ngoài được dùng, cấu hình provider phải tuân thủ chính sách dữ liệu; sản phẩm phải thông báo cho người dùng rằng code sẽ được xử lý bởi AI provider.
- Nội dung code, comment, tên file và rule custom phải được coi là dữ liệu, không phải lệnh hệ thống.

Giá trị giới hạn cụ thể (kích thước, số file, timeout) là cấu hình triển khai, phải có giá trị mặc định hữu hạn và trả lỗi/warning dễ hiểu khi chạm ngưỡng.

## 6. Functional Requirements

| ID | Mức | Yêu cầu và kết quả mong đợi |
|---|---|---|
| FR-01 | Must | Người dùng nhập URL GitHub public hợp lệ và tạo review repository. |
| FR-02 | Must | Người dùng xem/chọn nhánh hoặc commit; mặc định là nhánh mặc định. Mỗi review cố định owner, repo, ref được yêu cầu và commit SHA thực tế. |
| FR-03 | Must | Trước khi chạy, người dùng chọn rule set enabled; hệ thống lưu snapshot bất biến các rules/version áp dụng. |
| FR-04 | Must | Hệ thống tải và duyệt đệ quy source files thuộc repository đã chọn; hiển thị số file được quét/bỏ qua và nguyên nhân bỏ qua. |
| FR-05 | Must | Người dùng paste code để review mà không cần repository; chọn rule set, ngôn ngữ và tên file tùy chọn. |
| FR-06 | Must | Người dùng tạo/sửa/xóa/bật/tắt rule cá nhân và tạo/sửa/xóa rule set cá nhân. Không xóa rule đang được tham chiếu mà làm mất snapshot review cũ. |
| FR-07 | Must | Mỗi finding chính thức phải tham chiếu rule trong snapshot và có severity, tiêu đề, giải thích, bằng chứng từ code và gợi ý sửa. |
| FR-08 | Must | Finding có `filePath`, `lineStart`, `lineEnd` khi xác định được. Dòng phải tính theo file gốc hoặc nội dung paste; không bịa dòng. Nếu không chắc, trả finding không có line và nói rõ. |
| FR-09 | Must | Hệ thống hiển thị review status: `QUEUED`, `RUNNING`, `COMPLETED`, `COMPLETED_WITH_WARNINGS`, `FAILED`, `CANCELLED`; có lỗi/warning thân thiện khi cần. |
| FR-10 | Must | Trang kết quả có summary số findings theo severity và filter theo severity, rule, file; nhấn finding mở đúng file/dòng khi có. |
| FR-11 | Must | Người dùng xem danh sách review trước đây, thời điểm, nguồn input, commit SHA (với GitHub), rule set và trạng thái. |
| FR-12 | Should | Người dùng đánh dấu finding là hữu ích, không liên quan hoặc false positive; lưu phản hồi cùng người dùng và thời gian. |
| FR-13 | Must | Khi lỗi GitHub, vượt giới hạn hoặc AI provider lỗi, không tạo findings thiếu căn cứ; trạng thái và thông báo lỗi phải phản ánh phần việc thực sự hoàn thành. |

## 7. Định nghĩa Rule và Finding

### Rule

Rule tối thiểu gồm:

- `id`: ID ổn định do hệ thống sinh.
- `name`, `description`, `category`.
- `severity`: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`.
- `languages`: một hoặc nhiều ngôn ngữ; có thể là `ALL`.
- `instruction`: mô tả điều kiện vi phạm và hướng dẫn để AI nhận diện.
- `enabled`, `version`, `ownerId`, `createdAt`, `updatedAt`.

Rule set gồm `id`, `name`, `description`, `ownerId`, `enabled` và danh sách ID/version rules. Không cho submit với rule set disabled hoặc rỗng.

### Finding

Finding tối thiểu gồm:

- `id`, `ruleId`, `ruleVersion`, `severity`.
- `title`, `explanation`, `evidence`, `suggestedFix`.
- `filePath`, `lineStart`, `lineEnd` có thể null khi không xác định chắc chắn.
- `source`: `STATIC` hoặc `AI`.
- `status`: mặc định `OPEN`; nếu có phản hồi thì lưu riêng trạng thái/feedback của người dùng.

Finding trùng cùng rule và cùng vùng code chỉ hiển thị một lần. Không trả nhận xét chung không gắn được với rule đã chọn như một finding.

## 8. Màn hình cần có

1. **Tạo review:** tab/chọn nguồn `GitHub Repository` hoặc `Paste Code`; nhập URL/ref hoặc code; chọn rule set; chọn ngôn ngữ khi paste; hiển thị validation và giới hạn input.
2. **Reviews:** danh sách review có trạng thái, nguồn, thời điểm, repo/commit hoặc tên file paste, rule set.
3. **Review detail:** progress/status, commit SHA, số file quét/bỏ qua, summary, filter và danh sách findings.
4. **Rule sets:** danh sách, tạo/sửa, chọn rules, bật/tắt.
5. **Rules:** danh sách, tạo/sửa/xem phiên bản, bật/tắt/xóa rule cá nhân.

Giao diện phải hiển thị rõ nội dung lỗi và bước khắc phục. Code/snippet phải được escape khi render; không diễn giải code như HTML.

## 9. API và dữ liệu tối thiểu

API dùng JSON qua HTTPS, versioned route, kiểm tra input và quyền sở hữu tài nguyên. Tối thiểu cần các capability sau:

| Capability | Hành vi |
|---|---|
| Validate GitHub repository | Kiểm tra URL public, trả metadata và refs khả dụng. |
| Create repository review | Nhận URL, ref tùy chọn, rule set; chụp commit SHA, tạo review và trả ID/status. |
| Create pasted-code review | Nhận code, tên file/ngôn ngữ tùy chọn, rule set; trả ID/status. |
| Get review/list reviews | Trả metadata, trạng thái, progress, warnings, summary theo owner hiện tại. |
| Get findings | Trả findings có phân trang và filter. |
| CRUD rules/rule sets | Chỉ thao tác tài nguyên do owner hiện tại sở hữu. |
| Submit finding feedback | Lưu phản hồi của owner cho finding thuộc review của họ. |

Các entity lưu trữ tối thiểu:

- `User` (hoặc principal từ hệ thống xác thực hiện có).
- `Rule`, `RuleSet` và snapshot/version của rule.
- `Review`: owner, input type, repository/ref/commit hoặc metadata paste, trạng thái, timestamps, rule snapshot, warning/error.
- `ReviewFile`: path, content hash, trạng thái phân tích; source content chỉ lưu trong thời gian cần thiết theo retention cấu hình.
- `Finding`: thuộc review, rule snapshot, severity, evidence, gợi ý và vị trí.
- `FindingFeedback`: finding, owner, loại phản hồi, thời điểm.

Không trả raw stack trace, secret, token hay nội dung source trong error response/log.

## 10. Yêu cầu phi chức năng

- **Bảo mật:** HTTPS; xác thực người dùng; kiểm tra quyền sở hữu ở mọi endpoint; secrets lưu bằng cấu hình an toàn, không đặt trong frontend/repository.
- **Riêng tư:** source code được xem là dữ liệu nhạy cảm. Không lưu lâu hơn cần thiết; không log raw source. Lưu hash/metadata để truy vết khi có thể.
- **Độ tin cậy:** review dùng commit SHA và rule snapshot cố định; mọi lỗi một phần phải thể hiện trong kết quả.
- **Khả năng sử dụng:** hiển thị severity bằng text/icon, không phụ thuộc màu; hỗ trợ xem kết quả trên màn hình phổ biến.
- **Giới hạn tài nguyên:** timeout, dung lượng, số file và quota có cấu hình; thao tác dài xử lý bất đồng bộ, tránh request treo.
- **Khả năng quan sát:** log có correlation/review ID, stage, duration và error code; không đưa code/secret vào log.

Các mục tiêu số về latency, concurrency, retention và SLA cần được chốt khi triển khai; chúng không được dùng để bỏ kiểm tra input, quyền truy cập hoặc tính toàn vẹn của kết quả.

## 11. Luồng xử lý và lỗi

1. Validate user, input, rule set và giới hạn.
2. Với GitHub: xác thực host/URL, lấy ref và commit SHA; chỉ tải nội dung public từ GitHub. Không theo redirect ra host tùy ý.
3. Chụp rule set và các phiên bản rule; tạo review `QUEUED`.
4. Lọc file theo loại/giới hạn, tạo line map, phân tích từng file theo rule và ngôn ngữ áp dụng.
5. Kiểm tra output: rule ID phải nằm trong snapshot, severity phải hợp lệ, vị trí phải nằm trong file, evidence phải liên quan code. Loại output sai và ghi warning.
6. Lưu findings, summary, số file quét/bỏ qua và cập nhật trạng thái cuối.

Hành vi lỗi:

- URL sai, repository private/không tồn tại, ref sai, rule set không hợp lệ: từ chối tạo review với mã lỗi ổn định và thông điệp rõ.
- Rate limit hoặc GitHub không khả dụng: review `FAILED` hoặc cho retry có kiểm soát; không giả lập kết quả.
- File binary/không hỗ trợ/quá giới hạn: bỏ qua có thống kê; nếu không còn file nào phân tích được, review `FAILED` với lý do.
- Một số file/rule lỗi: `COMPLETED_WITH_WARNINGS` nếu còn kết quả hợp lệ; nếu không có kết quả, `FAILED`.
- AI trả output sai schema, rule không được chọn hoặc vị trí không hợp lệ: không hiển thị như finding chính thức.
- Retry không được tạo bản ghi finding trùng; review tiếp tục dùng commit và rule snapshot ban đầu.

## 12. Tiêu chí nghiệm thu

1. URL repository public và ref hợp lệ tạo review; báo cáo lưu đúng commit SHA thực tế.
2. URL sai, repo private/không tồn tại, ref không có hoặc rule set rỗng/disabled được từ chối với lý do cụ thể.
3. Review repository quét nhiều file source được hỗ trợ, bỏ qua file không hỗ trợ theo policy, và thống kê chính xác số file cùng nguyên nhân.
4. Review paste code cho kết quả có vị trí dòng đúng theo nội dung đã paste.
5. Mỗi finding hiển thị khớp rule đã chọn, severity, bằng chứng, giải thích, gợi ý sửa và vị trí khi xác định được.
6. Kết quả không có finding ngoài rule set đã chọn; output không hợp lệ không được trình bày như finding xác thực.
7. Người dùng tạo rule và rule set, dùng chúng cho review; chỉnh sửa rule không làm thay đổi kết quả review cũ.
8. Người dùng A không thể đọc/sửa review, finding, rule hay rule set của người dùng B qua UI hoặc gọi API trực tiếp.
9. Review dài có thể theo dõi trạng thái; lỗi GitHub/provider/giới hạn được biểu diễn trung thực, không để trạng thái chạy vô hạn.
10. Tải lại trang review detail vẫn hiển thị cùng kết quả đã lưu.

## 13. Giả định kỹ thuật để generate code

- Giữ cấu trúc module hiện có: `code-review-web` là React frontend; `code-review-api` là Java 17 backend; `code-review-ai` chịu trách nhiệm phân tích AI. Kiểm tra README/build files hiện có trước khi thêm framework/thư viện.
- API là nguồn chuẩn về xác thực, phân quyền, validation, lưu trữ và trạng thái review. Frontend không gọi AI provider trực tiếp.
- Định dạng trao đổi giữa API và AI phải là JSON schema có validation; agent không tự ghi database/API thay mặt người dùng.
- Tạo các adapter riêng cho GitHub và AI provider để thay cấu hình mà không thay đổi luồng nghiệp vụ. Chỉ dùng API công khai/clone read-only của public repository.
- Không tự thêm chức năng nằm ngoài MVP trong lúc generate code. Khi chi tiết chưa quy định (ví dụ database hoặc provider), ưu tiên dependency/cấu hình hiện hữu trong repository; nếu chưa có thì chọn phương án đơn giản có thể thay thế, ghi rõ trong README và không hard-code credential.
- Mọi endpoint phải có request/response/error schema nhất quán; mọi trạng thái nghiệp vụ phải được biểu diễn rõ ở API và UI.

## 14. Glossary

| Thuật ngữ | Định nghĩa |
|---|---|
| Rule | Quy tắc mô tả một điều kiện code cần phát hiện và cách giải thích/gợi ý sửa. |
| Rule set | Tập hợp rules người dùng chọn cho một lần review. |
| Finding | Một vi phạm cụ thể gắn với rule, bằng chứng và vị trí code nếu xác định được. |
| Commit SHA | Mã định danh bất biến của phiên bản source code được review. |
| Snapshot | Bản chụp input và phiên bản rules dùng cho một review để kết quả có thể truy vết. |
