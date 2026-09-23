# Guidance: Generate Prompts To Fix Backend Compile Errors

## 1. Mục tiêu

Tài liệu này hướng dẫn cách viết prompt cho AI để sửa compile error trong hai Maven module Java:

- `code-review-ai`
- `code-review-api`

Mục tiêu cuối cùng:

- Không còn compile error.
- Giữ Java 17.
- Giữ Maven multi-module.
- Giữ Spring Boot 3.x.
- Backend khởi động bình thường.
- Không làm thay đổi nghiệp vụ ngoài phạm vi sửa lỗi.

Phạm vi yêu cầu được mô tả trong [`requirements.md`](../requirements.md).

## 2. Nguyên tắc viết prompt

Một prompt tốt cần yêu cầu AI:

1. Đọc source code và `pom.xml` trước khi sửa.
2. Chạy build để lấy lỗi thực tế.
3. Gom lỗi theo nhóm.
4. Sửa lỗi theo từng nhóm nhỏ.
5. Chạy lại compile sau mỗi lần sửa.
6. Không tự đoán API hoặc dependency nếu chưa có bằng chứng.
7. Không xóa code để tránh compile error.
8. Không sửa frontend khi task chỉ liên quan backend.
9. Không tuyên bố thành công nếu chưa chạy validation.

Không nên dùng prompt quá chung như:

```text
Hãy sửa toàn bộ backend cho tôi.
```

Prompt này dễ khiến AI thay đổi quá nhiều file, tự tạo API không cần thiết hoặc che giấu lỗi thay vì sửa nguyên nhân gốc.

## 3. Quy trình thực hiện từng bước

### Bước 1: Kiểm tra môi trường

Mở terminal tại thư mục gốc project và chạy:

```powershell
java -version
mvn -version
```

Yêu cầu tối thiểu:

- Java 17.
- Maven đã được cài đặt.
- Biến môi trường `JAVA_HOME` trỏ đến JDK 17.

Prompt tương ứng:

```text
Bạn là Senior Java Developer.

Hãy kiểm tra môi trường hiện tại cho Maven backend:
- Java version
- Maven version
- JAVA_HOME

Yêu cầu project dùng Java 17.
Chỉ báo cáo kết quả và cảnh báo nếu môi trường không phù hợp.
Chưa chỉnh sửa source code.
```

### Bước 2: Khảo sát project

Dùng prompt sau để AI đọc cấu trúc trước khi sửa:

```text
Bạn là Senior Java Developer.

Hãy khảo sát backend trong workspace hiện tại, tập trung vào:
- code-review-ai
- code-review-api
- pom.xml ở thư mục gốc
- pom.xml của từng module
- requirements.md

Chỉ đọc và phân tích, chưa chỉnh sửa file.

Hãy trả về:
1. Danh sách module và vai trò của từng module.
2. Quan hệ dependency giữa các module.
3. Java version và Spring Boot version.
4. Lệnh build phù hợp.
5. Các khu vực có nguy cơ compile error.
6. Thứ tự xử lý đề xuất.

Không phỏng đoán nếu chưa đọc source code hoặc cấu hình Maven.
```

### Bước 3: Xác định nguồn compile error

Lỗi hiển thị trong IDE và lỗi do Maven có thể không giống nhau.

| Kết quả | Kết luận ban đầu |
|---|---|
| Maven thất bại | Có lỗi build/compile thực tế trong Maven project. |
| Maven thành công nhưng IDE gạch đỏ | Có thể IDE chưa reload Maven classpath, dùng JDK khác, mở sai project hoặc còn error marker cũ. |

Với project này, hãy chạy đúng từ thư mục chứa parent `pom.xml`:

```powershell
Get-Location
Test-Path .\pom.xml
mvn -version
mvn -pl code-review-api -am clean compile -DskipTests
```

Lệnh `-pl code-review-api -am` compile API cùng module AI mà API phụ thuộc.

Nếu lệnh trên thành công, chạy thêm:

```powershell
mvn clean compile -DskipTests
mvn clean verify -DskipTests
```

Phân biệt kết quả:

- `compile` thành công: source chính đã compile bằng Maven.
- `test-compile` thất bại: lỗi nằm ở test source.
- `verify` thất bại: có thể do test, coverage hoặc plugin, không nhất thiết là compile error.

#### Nếu dùng Eclipse hoặc Spring Tool Suite

Ảnh lỗi có giao diện Eclipse/STS. Khi Maven thành công nhưng IDE vẫn báo đỏ:

1. Mở workspace chứa parent `pom.xml`, không chỉ mở riêng `code-review-api`.
2. Chuột phải project parent và các module, chọn `Maven > Update Project...` hoặc nhấn `Alt+F5`.
3. Chọn `Force Update of Snapshots/Releases` nếu cần rồi bấm `OK`.
4. Kiểm tra `Properties > Java Build Path > Libraries` dùng JDK 17.
5. Kiểm tra compiler compliance level là Java 17.
6. Chọn `Project > Clean...`, clean các module rồi build lại.
7. Nếu còn error marker cũ, đóng/mở project hoặc restart IDE.
8. Không sửa source chỉ để xóa gạch đỏ trước khi Maven tái hiện được lỗi.

Nếu vẫn còn lỗi, copy nội dung **Problems view**, không chỉ gửi ảnh. Cần có message đầy đủ, file, line, error type và project/module.

Prompt phân biệt lỗi Maven và lỗi IDE:

```text
Maven compile đang thành công nhưng IDE vẫn hiển thị compile error.

Thông tin:
- IDE: [ECLIPSE/STS/INTELLIJ/VSCODE]
- Workspace/project: [PATH]
- Lệnh đã chạy: mvn -pl code-review-api -am clean compile -DskipTests
- Maven result: SUCCESS

Danh sách lỗi từ IDE Problems view:
[PASTE_FULL_PROBLEMS_VIEW_HERE]

Hãy phân loại từng lỗi là:
1. Lỗi source có thể tái hiện bằng compiler.
2. Lỗi IDE classpath/build path.
3. Error marker cũ.
4. Mở sai project/module.
5. Lỗi test hoặc generated source.

Trước khi sửa Java source, đề xuất cách đồng bộ Maven project, JDK 17 và IDE classpath.
Chỉ sửa source nếu có bằng chứng lỗi tái hiện được bằng Maven hoặc compiler tương ứng.
```

### Bước 4: Phân tích log Maven nếu Maven thất bại

Chạy từ thư mục gốc:

```powershell
mvn -pl code-review-api -am clean compile -DskipTests -e
```

Lưu lại các dòng `COMPILATION ERROR`, `cannot find symbol`, `package ... does not exist`, `method ... cannot be applied`, `incompatible types` và `Could not resolve dependencies`.

Prompt xử lý log:

```text
Bạn là Senior Java Developer.

Đây là toàn bộ output Maven compile:
[PASTE_FULL_MAVEN_LOG_HERE]

Hãy:
1. Liệt kê từng lỗi.
2. Gom lỗi theo missing dependency, import/package, method/constructor, generic/type và version thư viện.
3. Xác định nguyên nhân gốc và file cần sửa.
4. Đề xuất thứ tự sửa tối thiểu.

Chưa chỉnh sửa file trong bước này. Không suy đoán nếu log chưa đủ.
```

### Bước 5: Sửa dependency hoặc import

Lỗi dependency có thể tạo ra nhiều lỗi giả ở source Java.

```text
Hãy sửa nhóm lỗi dependency/import sau:
[PASTE_ERROR_GROUP]

Đọc parent pom.xml và pom.xml của module trước khi sửa.
Xác định class/package bị thiếu thuộc thư viện nào, kiểm tra scope/version và ưu tiên dependency management của Spring Boot.
Không thêm thư viện AI nếu lỗi không yêu cầu. Giữ Java 17 và Spring Boot 3.x.

Áp dụng patch tối thiểu, chạy lại compile module bị ảnh hưởng và báo kết quả.
```

### Bước 6: Sửa lỗi class, method và type

```text
Hãy sửa compile error sau mà không thay đổi behavior:
[PASTE_FULL_MAVEN_ERROR]

File lỗi: [FILE_PATH]

Trước khi sửa, đọc definition, method/constructor signature và các call site liên quan.
Kiểm tra import, generic, kiểu dữ liệu và compatibility với Java 17/Spring Boot 3.x.

Áp dụng patch nhỏ nhất, giữ public API nếu có thể, không đổi business logic và chạy lại Maven compile.
```

### Bước 7: Lặp lại compile theo module

Sau mỗi nhóm sửa:

```powershell
mvn -pl code-review-ai -am clean compile -DskipTests
mvn -pl code-review-api -am clean compile -DskipTests
mvn clean verify -DskipTests
```

Nếu Maven thành công nhưng IDE còn đỏ, quay lại Bước 3 và xử lý đồng bộ IDE; không tiếp tục sửa source theo error marker chưa được xác minh.

### Bước 8: Khởi động backend

Sau khi build thành công, khởi động API:

```powershell
mvn -pl code-review-api -am spring-boot:run
```

Nếu project có Maven wrapper, có thể dùng:

```powershell
./mvnw -pl code-review-api -am spring-boot:run
```

Kiểm tra backend đã listen đúng port bằng request HTTP hoặc trình duyệt.

Prompt:

```text
Maven build đã thành công. Hãy khởi động module code-review-api.

Kiểm tra:
1. Spring Boot có start thành công không.
2. Có lỗi bean, configuration hoặc port không.
3. Backend có listen đúng port cấu hình không.
4. Có exception lúc startup không.

Không tuyên bố backend chạy thành công nếu process bị dừng hoặc startup có error.
```

### Bước 9: Kiểm tra endpoint

Nếu API có health endpoint, gọi endpoint đó:

```powershell
Invoke-WebRequest http://localhost:8080/api/health
```

Hoặc nếu backend dùng prefix `/api/v1`:

```powershell
Invoke-WebRequest http://localhost:8080/api/v1/health
```

Prompt kiểm tra endpoint:

```text
Backend đang chạy.

Hãy kiểm tra các endpoint chính bằng HTTP request.
Với mỗi endpoint, báo:
- HTTP method
- URL
- HTTP status
- response body
- lỗi nếu có

Đối chiếu URL với api-spec.md và source controller hiện tại.
Không tự đổi endpoint chỉ vì request bị sai URL.
```

## 4. Prompt đầy đủ cho toàn bộ task

Có thể dùng prompt sau cho một phiên xử lý hoàn chỉnh:

```text
Bạn là Senior Java Developer.

Đọc requirements.md trong workspace và xử lý mục tiêu:
“Fix all compile errors in code-review-ai and code-review-api, then make the backend run normally.”

Quy tắc bắt buộc:
- Đọc cấu trúc project, pom.xml và source code trước khi sửa.
- Không revert các thay đổi có sẵn.
- Không sửa frontend.
- Không thêm chức năng mới.
- Không xóa code để né compile error.
- Giữ Java 17.
- Giữ Maven multi-module.
- Giữ Spring Boot 3.x.
- Không tự tạo API hoặc dependency nếu chưa có bằng chứng từ lỗi/build.
- Ưu tiên sửa dependency và compile blocker trước.
- Sau mỗi nhóm sửa phải chạy lại compile.
- Chỉ kết luận thành công sau khi build và startup được xác nhận.

Quy trình:
1. Khảo sát module ai và api.
2. Chạy mvn clean compile.
3. Gom compile error theo nhóm.
4. Sửa từng nhóm với patch nhỏ nhất.
5. Chạy lại compile sau mỗi nhóm.
6. Chạy mvn clean verify.
7. Khởi động code-review-api.
8. Gọi health endpoint và các endpoint chính.

Báo cáo cuối theo format:
1. Compile errors ban đầu.
2. Nguyên nhân từng nhóm lỗi.
3. Các file đã sửa.
4. Dependency đã thêm hoặc thay đổi.
5. Các lệnh validation đã chạy.
6. Kết quả build.
7. Kết quả startup backend.
8. Kết quả endpoint checks.
9. Lỗi còn lại hoặc giới hạn chưa kiểm chứng.

Không được báo “hoàn tất” nếu chưa có kết quả validation tương ứng.
```

## 5. Prompt review một file cụ thể

Dùng khi đã biết một file gây lỗi:

```text
Hãy review và sửa file sau:

[FILE_PATH]

Mục tiêu là loại bỏ compile error, không refactor ngoài phạm vi cần thiết.

Kiểm tra:
- package và import
- class/interface được tham chiếu
- method và constructor call
- generic và kiểu dữ liệu
- annotation
- dependency tương ứng trong pom.xml
- compatibility với Java 17 và Spring Boot 3.x
- các call site liên quan

Hãy:
1. Chỉ ra lỗi cụ thể.
2. Giải thích nguyên nhân.
3. Áp dụng patch tối thiểu.
4. Không đổi behavior hiện có.
5. Chạy compile cho module liên quan.
6. Báo kết quả validation.
```

## 6. Những điều cần tránh

### Không yêu cầu AI sửa quá rộng

Không dùng:

```text
Refactor và sửa tất cả backend cho sạch hơn.
```

Nên dùng:

```text
Chỉ sửa các lỗi compile được xác nhận bởi output Maven bên dưới.
Không refactor ngoài phạm vi các file liên quan.
```

### Không paste log thiếu context

Log nên bao gồm:

- tên module
- file lỗi
- dòng lỗi
- vài dòng đầu tiên của Maven error
- phần `Caused by` nếu có

### Không cho AI tự đoán dependency

Luôn yêu cầu AI đọc:

- parent `pom.xml`
- module `pom.xml`
- Spring Boot version
- Java version
- dependency tree nếu cần

### Không chỉ chạy test frontend

Frontend test không chứng minh backend compile. Backend phải được kiểm tra bằng:

```powershell
mvn clean verify
```

và sau đó chạy backend thực tế.

## 7. Checklist hoàn tất

- [ ] Đã đọc `requirements.md`.
- [ ] Java version là 17.
- [ ] Maven hoạt động.
- [ ] Đã chạy `mvn clean compile`.
- [ ] Đã xử lý missing dependency/import.
- [ ] Đã xử lý sai method/constructor/type.
- [ ] `mvn clean verify` không còn error.
- [ ] Backend khởi động thành công.
- [ ] Health endpoint trả HTTP 200.
- [ ] Các endpoint chính trả response đúng.
- [ ] Không sửa frontend ngoài phạm vi yêu cầu.
- [ ] Báo cáo cuối ghi rõ những phần chưa kiểm chứng, nếu có.
