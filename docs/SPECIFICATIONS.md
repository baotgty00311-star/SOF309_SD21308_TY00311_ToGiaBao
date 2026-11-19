# ĐẶC TẢ NGHIỆP VỤ WEB CÁ NHÂN (SPECIFICATIONS)
> *Dùng làm ngữ cảnh (Context) gửi cho AI để sinh Test Plan & Test Case chính xác theo dự án PetCare.*

---

## 1. THÔNG TIN DỰ ÁN
* **Tên đề tài:** Website Đặt lịch & Quản lý Chăm sóc Thú cưng **PetCare**
* **Sinh viên thực hiện:** Tô Gia Bảo — MSSV: `TY00311` — Lớp: `SD21308`
* **Công nghệ:** Java 17, Jakarta Servlet/JSP (Apache Tomcat 10.1), SQL Server (`mssql-jdbc`), BCrypt, JUnit 5, Selenium WebDriver 4
* **URL chạy local:** `http://localhost:8080/sof309-assignment`
* **Các màn hình / endpoint chính:**
  * Trang chủ & Giới thiệu dịch vụ: `/home`, `/trang-chu`, `/dat-lich` (`HomeServlet`)
  * Đăng nhập: `/login`, `/dang-nhap` (`LoginServlet`)
  * Đăng ký: `/register`, `/dang-ky` (`RegisterServlet`)
  * Hồ sơ & Đổi mật khẩu: `/profile`, `/ho-so` (`ProfileServlet`)
  * Đặt lịch dịch vụ khách hàng: `/customer/dat-lich` (`DatLichServlet`)
  * Quản lý tài khoản Admin: `/admin/tai-khoan` (`QuanLyTaiKhoanServlet`)

## 2. QUY TẮC NGHIỆP VỤ (BUSINESS RULES)
* **Chuẩn hóa & Kiểm tra dữ liệu (`ValidationUtil`):**
  * `normalizePhone`: loại bỏ khoảng trắng, dấu `.`, `-`, `(`, `)`, và đổi tiền tố `+84` thành `0`.
  * `isValidPhone`: chuỗi sau chuẩn hóa không rỗng, dài `<= 20` ký tự, khớp `^(0|\+84)[0-9]{8,10}$` (9–11 chữ số bắt đầu bằng `0`).
  * `normalizeEmail`: `trim()` và chuyển chữ thường (`Locale.ROOT`).
  * `isValidEmail`: không rỗng, dài `<= 100` ký tự, khớp regex `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`.
* **Chức năng Đăng ký (`AuthService.registerCustomer` & `RegisterServlet`):**
  * `fullName`: bắt buộc, sau `trim()` không được rỗng.
  * `phone`: bắt buộc, hợp lệ theo `ValidationUtil.isValidPhone` và không được trùng trong hệ thống.
  * `email`: tùy chọn khi gọi `AuthService.registerCustomer` — nếu để trống thì hệ thống tự sinh `<cleanPhone>@petcare.vn`; nếu nhập thì phải đúng định dạng và không được trùng.
  * `password`: bắt buộc, tối thiểu `8` ký tự (và khớp `confirmPassword` ở `RegisterServlet`).
  * Mật khẩu được băm bằng `BCrypt` (`cost = 12`), tài khoản mới mặc định có vai trò `CUSTOMER` và `isActive = true`.
* **Chức năng Đăng nhập (`AuthService.authenticate` & `LoginServlet`):**
  * Nhận `identifier` (SĐT hoặc Email) và `password`, đều không được rỗng/blank (`LoginServlet` yêu cầu thêm độ dài mật khẩu tối thiểu `6` ký tự).
  * Kiểm tra mật khẩu bằng `AuthService.verifyPassword` (hỗ trợ hash BCrypt `$2a$`/`$2b$`/`$2y$` hoặc so sánh an toàn thời gian hằng).
  * Nếu tài khoản bị khóa (`isActive == false`) -> ném `IllegalArgumentException` thông báo bị khóa.
  * Thành công: `ADMIN` chuyển hướng `/admin/tai-khoan`, các vai trò khác chuyển hướng `/home`.
* **Chức năng Hồ sơ & Đổi mật khẩu (`AuthService.updateProfile` & `changePassword`):**
  * Cập nhật họ tên (không rỗng), SĐT (hợp lệ, không trùng tài khoản khác), Email (hợp lệ, không rỗng, không trùng tài khoản khác).
  * Đổi mật khẩu: kiểm tra đúng mật khẩu hiện tại, mật khẩu mới tối thiểu `8` ký tự.
* **Chức năng Đặt lịch dịch vụ (`BookingService.bookService` & `DatLichServlet`):**
  * Kiểm tra thú cưng (`Pets`) tồn tại và `ownerId == customerId`; nếu không -> ném `IllegalArgumentException("Thú cưng không thuộc tài khoản của bạn.")`.
  * Kiểm tra dịch vụ (`Services`) tồn tại và `isActive() == true`; nếu không -> ném `IllegalArgumentException("Dịch vụ không tồn tại hoặc đã ngừng hoạt động.")`.
  * Tự động tính `scheduledEnd = scheduledStart.plusMinutes(service.getDurationMinutes())`.
  * Ánh xạ mã lỗi SQL Server từ stored procedure (`51001`, `51031`, `51032`, `51009`, `51010`, `51008`, và mặc định) sang thông báo tiếng Việt thân thiện.
* **Trang chủ & Thanh toán (`HomeServlet` & `AppointmentBalance`):**
  * `HomeServlet.buildDefaultServices()` cung cấp `6` gói dịch vụ mặc định; `buildDefaultReviews()` cung cấp `3` đánh giá mẫu.
  * `AppointmentBalance.getRemainingAmount()` trả về `totalAmount.subtract(paidOrPendingAmount)`.
