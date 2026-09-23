# Guidance: Generate Prompts For Code Comments And Javadoc

## 1. Mục tiêu

Guidance này dùng cho requirement #2 trong [`requirements.md`](../requirements.md): bổ sung comment/Javadoc cho project theo chuẩn trong [`coding-rules.md`](../coding-rules.md).

Mục tiêu là làm code dễ hiểu hơn mà không thay đổi behavior:

- Không đổi business logic.
- Không đổi API, payload, database schema hoặc method signature.
- Không thêm dependency.
- Không format lại file ngoài phạm vi cần thiết.
- Không thêm comment hiển nhiên hoặc comment sai với implementation.
- Comment phải giải thích mục đích, lý do hoặc ràng buộc quan trọng.

## 2. Nguyên tắc prompt

Mọi prompt nên yêu cầu AI thực hiện theo thứ tự:

1. Đọc `requirements.md` và `coding-rules.md`.
2. Đọc file mục tiêu và các class/method liên quan.
3. Lập danh sách đầy đủ mọi public Java class, interface, record, constructor, method và API.
4. Bắt buộc thêm Javadoc chuẩn `/** ... */` cho từng declaration trong danh sách, trừ getter/setter hiển nhiên theo coding rules.
5. Với từng Javadoc, kiểm tra `@param` cho mọi tham số/record component, `@return` cho API trả kết quả và `@throws` cho exception quan trọng hoặc exception được khai báo.
6. Chỉ thêm comment private hoặc inline khi có giá trị thông tin.
7. Không thay đổi logic trong cùng patch.
8. Kiểm tra diff để phát hiện thay đổi ngoài comment.
9. Chạy compile/test phù hợp.

Không nên dùng prompt chung như:

```text
Hãy thêm comment cho toàn bộ code.
```

Prompt này dễ tạo ra comment dạng kể lại code, làm file dài hơn nhưng không dễ bảo trì hơn.

## 3. Quy tắc chọn loại comment

| Đối tượng | Cách viết |
|---|---|
| Public class/interface/record | **Bắt buộc** có Javadoc `/** ... */` nêu trách nhiệm và boundary chính; record phải mô tả từng component bằng `@param`. |
| Public constructor/method/API | **Bắt buộc** có Javadoc nêu mục đích; dùng `@param` cho từng tham số, `@return` nếu có kết quả và `@throws` cho exception quan trọng/khi khai báo `throws`. |
| Private method đơn giản | Thường không cần comment nếu tên hàm đã rõ. |
| Block xử lý phức tạp | Comment ngắn giải thích `why`, invariant hoặc thứ tự bắt buộc. |
| Security/transaction/retry/timeout/concurrency | Nên có comment về constraint và lý do. |
| Getter/setter hiển nhiên | Không thêm comment máy móc; không dùng ngoại lệ này cho public API khác. |
| TODO/workaround | Chỉ giữ khi có context, lý do và bước tiếp theo rõ ràng. |

Mẫu Javadoc bắt buộc cho public method:

```java
/**
 * Adds two large non-negative numbers represented as digit strings.
 *
 * @param firstOperand first digit-string operand
 * @param secondOperand second digit-string operand
 * @return the sum represented as a digit string
 * @throws IllegalArgumentException if either operand is null, empty, or contains a non-digit
 */
public String add(String firstOperand, String secondOperand) {
	// implementation remains unchanged
}
```

Không sao chép máy móc mẫu trên: nội dung tag phải phản ánh đúng signature, output,
exception và invariant thực tế của từng API.

## 4. Prompt audit trước khi sửa

Dùng prompt này để AI chỉ phân tích, chưa chỉnh sửa:

```text
Bạn là Senior Fullstack Developer.

Hãy đọc:
- .docs/requirements.md
- .docs/coding-rules.md
- file source được chỉ định bên dưới

File cần audit:
[FILE_PATH]

Chỉ phân tích, chưa chỉnh sửa file.

Hãy trả về bảng gồm:
1. Toàn bộ class/interface/record/constructor/public method/API và trạng thái Javadoc của từng declaration.
2. Declaration public nào thiếu Javadoc hoặc thiếu `@param`, `@return`, `@throws` cần thiết; đây là lỗi bắt buộc phải xử lý.
3. Block logic nào cần comment giải thích lý do hoặc invariant.
4. Comment hiện có nào sai, lỗi thời hoặc dư thừa.
5. Các phần không nên thêm comment.
6. Xác nhận rằng việc thêm comment có thể thực hiện mà không đổi behavior.

Tuân thủ coding-rules.md. Audit chỉ được kết luận đạt khi mọi public Java declaration có Javadoc đúng format và đủ tag theo contract. Không đề xuất comment chỉ mô tả lại code từng dòng.
```

## 5. Prompt thêm comment cho một file Java

```text
Bạn là Senior Java Developer.

Hãy cập nhật comment/Javadoc cho file:
[FILE_PATH]

Đọc và tuân thủ:
- .docs/requirements.md, đặc biệt requirement #2
- .docs/coding-rules.md, mục “Quy tắc viết comment và Javadoc”

Yêu cầu:
- BẮT BUỘC thêm Javadoc chuẩn `/** ... */` cho mọi public class, interface, record, constructor, method và API trong file, trừ getter/setter hiển nhiên.
- Không được bỏ qua public declaration vì nó ngắn, một dòng hoặc chỉ forward sang service.
- Với mọi tham số hoặc record component phải có `@param`; method có return value phải có `@return`; method có exception quan trọng hoặc khai báo `throws` phải có `@throws`.
- Comment phải giải thích trách nhiệm, mục đích, input/output hoặc exception khi cần.
- Chỉ thêm comment cho private logic nếu có lý do không hiển nhiên, invariant, workaround hoặc constraint bên ngoài.
- Không thêm comment cho getter, setter, assignment hoặc câu lệnh hiển nhiên.
- Không thay đổi business logic, method signature, API, import, dependency, thứ tự xử lý hoặc format không liên quan.
- Không thêm secret, token, prompt nội bộ hoặc dữ liệu nhạy cảm.

Sau khi sửa:
1. Kiểm tra diff và xác nhận thay đổi chỉ liên quan comment/Javadoc.
2. Audit lại từng public Java declaration; nếu còn thiếu Javadoc hoặc tag contract thì chưa hoàn tất.
3. Chạy compile/test phù hợp với module.
4. Báo rõ các vùng đã thêm comment và lệnh validation đã chạy.
```

## 6. Prompt thêm comment cho một file Spring Controller/Service

```text
Hãy bổ sung Javadoc/comment có chọn lọc cho file Spring Boot sau:
[FILE_PATH]

Giữ nguyên hoàn toàn:
- endpoint và HTTP method
- request/response DTO
- status code
- validation
- transaction boundary
- exception handling
- security behavior
- service/repository calls

Comment cần làm rõ:
- trách nhiệm của controller/service
- ý nghĩa của public use case
- lý do của transaction, security, retry, timeout hoặc trạng thái nếu code có sử dụng
- boundary giữa HTTP mapping và business logic

Mọi public class, constructor, endpoint method và public API trong file đều bắt buộc có Javadoc; comment inline không thay thế được Javadoc. Endpoint Javadoc phải có `@param` cho path/query/body/header và `@return` cho response khi có.

Không comment lại annotation một cách máy móc và không refactor code.
Sau khi sửa hãy kiểm tra diff và compile module backend.
```

## 7. Prompt thêm comment cho AI module

```text
Hãy bổ sung comment/Javadoc cho file thuộc code-review-ai:
[FILE_PATH]

Tuân thủ coding-rules.md và giữ nguyên behavior.

Mọi public class, record, constructor và method phải có Javadoc chuẩn với `@param`, `@return`, `@throws` theo contract. Nếu audit phát hiện một declaration public thiếu Javadoc hoặc thiếu tag thì phải bổ sung trước khi báo hoàn tất.

Comment cần ưu tiên giải thích:
- input/output schema
- validation ở boundary
- rule snapshot và provenance của finding
- lý do không thực thi source code hoặc custom rule như command
- cách xử lý output AI không hợp lệ
- warning/error có cấu trúc

Không đưa API key, prompt bí mật, source code mẫu nhạy cảm hoặc dữ liệu người dùng vào comment.
Không thay đổi logic AI, schema hoặc public API.
```

## 8. Prompt cho frontend React/JavaScript

```text
Hãy bổ sung comment có chọn lọc cho file frontend:
[FILE_PATH]

Tuân thủ .docs/coding-rules.md.

Chỉ comment các phần cần giải thích:
- lý do của useEffect hoặc dependency đặc biệt
- polling, cleanup hoặc request cancellation
- state transition không hiển nhiên
- mapping dữ liệu API vào UI
- workaround do browser/library behavior

Không comment từng JSX element, hook đơn giản hoặc phép gán hiển nhiên.
Không thay đổi rendering, API call, state, text hiển thị hoặc behavior.
Kiểm tra diff sau khi sửa.
```

## 9. Prompt audit toàn project

Dùng sau khi đã thêm comment từng nhóm file:

```text
Hãy audit comment/Javadoc trong toàn bộ source code của project.

Phạm vi:
- code-review-ai
- code-review-api
- code-review-web

Đọc .docs/coding-rules.md và kiểm tra:
1. Mọi public Java class/interface/record/constructor/method/API đã có Javadoc chuẩn và đủ `@param`, `@return`, `@throws` theo contract chưa; thiếu Javadoc/tag là finding bắt buộc.
2. Comment có giải thích why thay vì lặp lại what không.
3. Comment có lỗi thời hoặc mâu thuẫn với code không.
4. Comment có chứa secret, token, prompt nội bộ hoặc dữ liệu nhạy cảm không.
5. Có file nào bị comment quá nhiều hoặc quá ít không.
6. Có thay đổi logic vô tình đi kèm việc thêm comment không.

Chỉ trả về report trước, chưa chỉnh sửa.
Report phải phân loại theo:
- Missing documentation
- Incorrect/outdated comment
- Redundant comment
- Sensitive information
- No action needed

Không được trả về `No action needed` hoặc kết luận đạt nếu còn bất kỳ public Java declaration nào thiếu Javadoc hoặc thiếu tag contract cần thiết.
```

## 10. Prompt kiểm tra không đổi logic

```text
Hãy review patch sau với mục tiêu xác nhận đây là comment-only change:

[PASTE_GIT_DIFF_HERE]

Kiểm tra xem patch có thay đổi bất kỳ nội dung nào ngoài comment/Javadoc không:
- Java statements
- JSX/JavaScript statements
- method signature
- annotation
- import
- string literal
- configuration
- API endpoint
- dependency

Ngoài việc kiểm tra comment-only, phải kiểm tra rằng mọi public Java declaration mới hoặc bị ảnh hưởng vẫn có Javadoc bắt buộc và đủ `@param`, `@return`, `@throws` theo signature.

Nếu có thay đổi ngoài comment, chỉ rõ file và dòng. Nếu không có, xác nhận patch chỉ bổ sung hoặc chỉnh comment.
```

## 11. Quy trình thực hiện từng bước

### Bước 1: Chọn phạm vi nhỏ

Không bắt đầu bằng toàn repository. Chọn một module hoặc một nhóm file liên quan:

```text
code-review-api/src/main/java/com/codereviewagent/api/controller
```

### Bước 2: Audit trước

Chạy prompt audit ở mục 4 và lưu danh sách nơi cần comment.

### Bước 3: Sửa theo nhóm

Ưu tiên:

1. Public Java API.
2. Controller/service có business rule.
3. Security/transaction/external integration.
4. AI boundary và validation.
5. Frontend effect/polling/state transition.

### Bước 4: Kiểm tra diff

Chạy:

```powershell
git diff --check
git diff -- .\code-review-ai .\code-review-api .\code-review-web
```

Nếu diff có thay đổi logic, dừng và yêu cầu AI tách thay đổi đó khỏi comment-only patch.

### Bước 5: Validation

Backend:

```powershell
mvn -pl code-review-api -am clean compile -DskipTests
```

Frontend:

```powershell
Set-Location .\code-review-web
npm test -- --run
npm run build
```

Không ghi nhận command là thành công nếu command chưa thực sự chạy hoặc bị skip.

## 12. Prompt hoàn chỉnh khuyến nghị

```text
Bạn là Senior Fullstack Developer.

Mục tiêu: thực hiện requirement #2 trong .docs/requirements.md: bổ sung comment/Javadoc cho project.

Đọc trước:
- .docs/requirements.md
- .docs/coding-rules.md
- file source và các dependency/call site liên quan

Phạm vi file:
[LIST_FILE_PATHS]

Quy tắc:
- BẮT BUỘC có Javadoc chuẩn `/** ... */` cho mọi public Java class, interface, record, constructor, method và API; getter/setter hiển nhiên là ngoại lệ duy nhất.
- BẮT BUỘC dùng `@param` cho từng tham số/record component, `@return` cho method trả kết quả và `@throws` cho exception quan trọng hoặc exception khai báo trong signature.
- Comment ngắn và giải thích why, invariant, constraint hoặc behavior không hiển nhiên.
- Không comment lại từng dòng code.
- Không thêm comment cho getter/setter hoặc code hiển nhiên.
- Không chứa secret, token, prompt nội bộ hoặc dữ liệu nhạy cảm.
- Không thay đổi behavior, public API, schema, endpoint, dependency hoặc format không liên quan.
- Không refactor trong task comment-only.

Quy trình:
1. Audit và liệt kê vị trí cần comment.
2. Thêm Javadoc đầy đủ cho từng public Java declaration trước khi thêm comment inline.
3. Review diff để xác nhận chỉ thay đổi comment.
4. Audit lại để xác nhận không còn public Java declaration thiếu Javadoc hoặc thiếu tag contract.
5. Chạy validation phù hợp.
6. Báo cáo file đã sửa, lý do thêm comment và kết quả validation.

Nếu phát hiện code có bug hoặc compile error, không tự sửa trong patch này. Ghi riêng issue đó để xử lý ở task khác.
Không báo hoàn tất nếu chưa kiểm tra diff và validation.
```

## 13. Checklist hoàn tất

- [ ] Đã đọc `requirements.md` và `coding-rules.md`.
- [ ] Đã audit trước khi thêm comment.
- [ ] Mọi public Java class/interface/record/constructor/method/API có Javadoc chuẩn; không còn declaration hoặc tag contract thiếu.
- [ ] Javadoc có `@param`, `@return`, `@throws` đúng với signature và behavior.
- [ ] Comment giải thích mục đích hoặc ràng buộc, không lặp lại code.
- [ ] Không có comment lỗi thời hoặc mâu thuẫn implementation.
- [ ] Không có secret/token/dữ liệu nhạy cảm trong comment.
- [ ] Không thay đổi behavior hoặc public API.
- [ ] `git diff --check` đã chạy.
- [ ] Backend compile hoặc frontend test/build đã chạy tùy phạm vi.
- [ ] Báo cáo ghi rõ phần chưa kiểm chứng nếu có.