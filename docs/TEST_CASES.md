# BỘ KỊCH BẢN KIỂM THỬ (TEST CASES)

## 1. BỘ UNIT TEST CHO AUTHENTICATION & VALIDATION (`AuthControllerTest.java`)

| Test Case ID | Chức năng | Mô tả ca kiểm thử | Loại test (Pos/Neg) | Kỹ thuật JUnit 5 | Dữ liệu kiểm thử (Test Data) | Kết quả mong đợi | Trạng thái |
| :--- | :---: | :--- | :---: | :--- | :--- | :--- | :---: |
| **TC_VAL_PHONE** | Validation | Chuẩn hóa và kiểm tra hợp lệ SĐT theo biên độ dài & tiền tố `+84` | Pos / Neg | `@ParameterizedTest` + `@CsvSource` | `""`, `"abc12345"`, `"09123"`, `"0912345678"`, `"+84 912-345-678"` | SĐT hợp lệ trả về `true` và chuẩn hóa `+84` thành `0912345678`; dữ liệu sai trả `false` | Pass |
| **TC_VAL_EMAIL** | Validation | Kiểm tra định dạng email hợp lệ và không hợp lệ | Pos / Neg | `@ParameterizedTest` + `@CsvSource` | `""`, `"nguyenvana.petcare.vn"`, `"nguyenvana@localhost"`, `"khachhang@petcare.vn"` | Chỉ `"khachhang@petcare.vn"` trả về `true` | Pass |
| **TC_REG_BOUNDARY** | Register | Kiểm tra giá trị biên form đăng ký không hợp lệ | Negative | `@ParameterizedTest` + `assertThrows` | Tên rỗng; SĐT `"09123"`; Email `"email-sai"`; Mật khẩu 7 ký tự `"1234567"` | Ném `IllegalArgumentException` đúng thông báo lỗi từng trường | Pass |
| **TC_REG_01** | Register | Đăng ký tài khoản thành công tại giá trị biên mật khẩu 8 ký tự | Positive | `@Test` + `@Timeout(5s)` | `"  Tô Gia Bảo  "`, `"+84 912-345-777"`, `"BaoTG@PetCare.vn"`, `"Pass@123"` | Tạo user `CUSTOMER`, `isActive=true`, SĐT `"0912345777"`, email `"baotg@petcare.vn"`, mật khẩu hash BCrypt khớp | Pass |
| **TC_REG_02** | Register | Đăng ký bỏ trống email tự động sinh email theo SĐT | Positive | `@Test` + `@Timeout(5s)` | `"Khách Mới"`, `"0912345888"`, `"   "`, `"Pass@1234"` | Tạo user thành công với email `"0912345888@petcare.vn"` | Pass |
| **TC_REG_DUP_PHONE** | Register | Đăng ký thất bại khi SĐT đã tồn tại trong hệ thống | Negative | `@Test` + `assertThrows` | SĐT trùng `"0912345678"` | Ném `IllegalArgumentException("Số điện thoại này đã được đăng ký tài khoản.")` | Pass |
| **TC_REG_DUP_EMAIL** | Register | Đăng ký thất bại khi Email đã tồn tại trong hệ thống | Negative | `@Test` + `assertThrows` | Email trùng `"khachhang@petcare.vn"` | Ném `IllegalArgumentException("Địa chỉ Email này đã được sử dụng.")` | Pass |
| **TC_AUTH_01** | Login | Đăng nhập thành công bằng SĐT hoặc Email hợp lệ | Positive | `@Test` + `@Timeout(5s)` | `"0912345678"` / `"Pass@1234"` và `"admin@petcare.vn"` / `"Pass@1234"` | Trả về đúng đối tượng `Users` (`CUSTOMER` id=1, `ADMIN` id=2) | Pass |
| **TC_AUTH_02** | Login | Đăng nhập thất bại khi bỏ trống SĐT/Email hoặc mật khẩu | Negative | `@Test` + `assertThrows` | `""` / `"Pass@1234"`, `"0912345678"` / `""`, `null` / `null` | Ném `IllegalArgumentException` | Pass |
| **TC_AUTH_03** | Login | Đăng nhập thất bại khi nhập sai mật khẩu | Negative | `@Test` + `assertThrows` | `"0912345678"` / `"WrongPass@1"` | Ném `IllegalArgumentException("Số điện thoại/Email hoặc mật khẩu không chính xác.")` | Pass |
| **TC_AUTH_04** | Login | Đăng nhập thất bại khi tài khoản đang bị khóa | Negative | `@Test` + `assertThrows` | `"0988777666"` / `"Pass@1234"` (`isActive=false`) | Ném `IllegalArgumentException` chứa thông báo `"bị khóa"` | Pass |
| **TC_PROF_01** | Profile | Cập nhật hồ sơ và đổi mật khẩu thành công | Positive | `@Test` + `@Timeout(5s)` | Đổi tên/SĐT/Email và đổi mật khẩu sang `"NewPass@999"` | Cập nhật thành công và đăng nhập lại bằng mật khẩu mới được | Pass |
| **TC_PROF_02** | Profile | Đổi mật khẩu thất bại khi sai mật khẩu cũ hoặc mật khẩu mới `< 8` ký tự | Negative | `@Test` + `assertThrows` | Mật khẩu cũ sai `"SaiMatKhau"`; mật khẩu mới `"1234567"` | Ném `IllegalArgumentException` | Pass |
| **TC_HASH_01** | Security | `AuthService.hashPassword` từ chối mật khẩu rỗng hoặc null | Negative | `@Test` + `assertThrows` | `""`, `null` | Ném `IllegalArgumentException` | Pass |

---

## 2. BỘ UNIT TEST CHO BOOKING, HOME & PAYMENT (`BookingControllerTest.java`)

| Test Case ID | Chức năng | Mô tả ca kiểm thử | Loại test (Pos/Neg) | Kỹ thuật JUnit 5 | Dữ liệu kiểm thử (Test Data) | Kết quả mong đợi | Trạng thái |
| :--- | :---: | :--- | :---: | :--- | :--- | :--- | :---: |
| **TC_BOOK_01** | Booking | Đặt lịch dịch vụ thành công và tự động cộng thời lượng vào `scheduledEnd` | Positive | `@Test` + `@Timeout(2s)` | `customerId=10`, `petId=1`, `serviceId=1` (45 phút), `staffId=5`, `start=09:30` | Trả về `appointmentId=101`, `scheduledEnd = 10:15` | Pass |
| **TC_BOOK_02** | Booking | Đặt lịch thất bại khi `petId` không tồn tại | Negative | `@Test` + `assertThrows` | `customerId=10`, `petId=999` | Ném `IllegalArgumentException("Thú cưng không thuộc tài khoản của bạn.")` | Pass |
| **TC_BOOK_03** | Booking | Đặt lịch thất bại khi thú cưng thuộc khách hàng khác | Negative | `@Test` + `assertThrows` | `customerId=10`, `petId=2` (`ownerId=20`) | Ném `IllegalArgumentException("Thú cưng không thuộc tài khoản của bạn.")` | Pass |
| **TC_BOOK_04** | Booking | Đặt lịch thất bại khi dịch vụ không tồn tại hoặc đã ngừng hoạt động | Negative | `@Test` + `assertThrows` | `serviceId=999`, `serviceId=2` (`active=false`) | Ném `IllegalArgumentException("Dịch vụ không tồn tại hoặc đã ngừng hoạt động.")` | Pass |
| **TC_BOOK_SQL_MAP** | Booking | Ánh xạ mã lỗi SQL Server sang thông báo tiếng Việt thân thiện | Negative | `@ParameterizedTest` + `assertThrows` | Mã lỗi SQL: `51001`, `51031`, `51032`, `51009`, `51010`, `51008`, `99999` | Ném `SQLException` với thông báo tiếng Việt tương ứng và giữ nguyên `cause` | Pass |
| **TC_HOME_01** | Home | Danh sách dịch vụ mặc định trên trang chủ đầy đủ tiêu đề & giá | Positive | `@Test` | `HomeServlet.buildDefaultServices()` | Trả về 6 `ServiceCard` có `title` và `priceValue` hợp lệ | Pass |
| **TC_HOME_02** | Home | Danh sách đánh giá khách hàng mặc định trên trang chủ | Positive | `@Test` | `HomeServlet.buildDefaultReviews()` | Trả về 3 `ReviewItem` có `authorName` và `quote` hợp lệ | Pass |
| **TC_PAY_01** | Payment | Tính số tiền còn lại cần thanh toán của lịch hẹn | Positive | `@Test` | `totalAmount=350000`, `paidOrPendingAmount=100000` | `getRemainingAmount()` trả về `250000` | Pass |

---

## 3. BỘ TEST MẪU & SELENIUM UI (HIỆN CÓ)

| Test Case ID | Chức năng | Mô tả ca kiểm thử | Loại test (Pos/Neg) | File thực thi | Dữ liệu kiểm thử | Kết quả mong đợi | Trạng thái |
| :--- | :---: | :--- | :---: | :--- | :--- | :--- | :---: |
| **TC_SAMPLE_01..02** | Sample | Kiểm tra mẫu logic đăng nhập đúng & mật khẩu rỗng | Pos / Neg | `SampleControllerTest.java` | `"admin"/"123"`, `"admin"/""` | Pass cả 2 ca | Pass |
| **TC_UI_LOGIN_01** | Login UI | Kiểm tra đăng nhập trên trình duyệt thực tế bằng POM | Positive | `LoginTest.java` | `"admin"/"123"` (`@Disabled`) | Chuyển sang `/home` hoặc `/dashboard` | Skipped (chờ Y4) |
