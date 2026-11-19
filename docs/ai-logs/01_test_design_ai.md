# Nhật ký AI: Thiết kế Test Plan & Bộ Test Case bằng AI RAG (Y2)

## 1. Ngữ cảnh & Mục tiêu
- Áp dụng cho dự án **PetCare** (`SOF309_SD21308_TY00311_ToGiaBao`) — Tô Gia Bảo (`TY00311`, lớp `SD21308`).
- Mục tiêu: Sử dụng đặc tả nghiệp vụ trong `docs/SPECIFICATIONS.md` kết hợp mã nguồn `AuthService`, `BookingService`, `ValidationUtil`, `HomeServlet`, `AppointmentBalance` làm ngữ cảnh cho AI sinh `docs/TEST_PLAN.md` và `docs/TEST_CASES.md` theo kỹ thuật **Phân vùng tương đương** và **Phân tích giá trị biên**.

## 2. Câu lệnh đã gửi cho AI (Prompt)
> "Bạn là QA Lead. Dưới đây là đặc tả nghiệp vụ và mã nguồn dự án Web PetCare của tôi (`docs/SPECIFICATIONS.md`, `AuthService.java`, `BookingService.java`, `ValidationUtil.java`, `HomeServlet.java`, `AppointmentBalance.java`). Hãy áp dụng kỹ thuật Phân vùng tương đương và Phân tích giá trị biên để thiết kế Test Plan (`docs/TEST_PLAN.md`) và Bộ Test Case chi tiết (`docs/TEST_CASES.md`) phục vụ viết Unit Test JUnit 5."

## 3. Kết quả do AI sinh ra (Raw Output)
```markdown
# KẾ HOẠCH KIỂM THỬ (Gợi ý ban đầu của AI)
- Kiểm thử đăng ký: kiểm tra username, email, password (tối thiểu 8 ký tự), confirmPassword.
- Kiểm thử đăng nhập: đúng tài khoản -> trang chủ, sai -> báo lỗi.
- Kiểm thử CRUD sản phẩm: thêm mới tên sản phẩm không trùng, giá > 0, số lượng >= 0.

# BỘ TEST CASE (Gợi ý ban đầu của AI)
| ID | Mô tả | Dữ liệu | Mong đợi |
| TC_01 | Đăng ký với username ngắn (< 5 ký tự) | user / 12345678 | Báo lỗi username |
| TC_02 | Đăng ký bỏ trống email | Nguyen Van A / 0912345678 / "" / 12345678 | Báo lỗi "Email không được để trống" |
| TC_03 | Đặt lịch thất bại khi trùng lịch | petId=1, serviceId=1 | Trả về false |
```

## 4. Chỉnh sửa của Sinh viên & Lý do sửa
- **Những điểm AI làm sai hoặc thiếu so với code thực tế của PetCare:**
  1. AI nhầm sang template web bán hàng (có `username` và `Product CRUD`) trong khi dự án **PetCare** đăng nhập/đăng ký bằng **SĐT (`phone`) hoặc `email`** (không có trường `username`) và nghiệp vụ chính là **Đặt lịch chăm sóc thú cưng (`BookingService`)**.
  2. Ở `TC_02`, AI dự đoán bỏ trống email khi gọi `AuthService.registerCustomer` sẽ báo lỗi. Thực tế kiểm tra `AuthService.java` (dòng 58–60) cho thấy khi `email` rỗng, hệ thống **tự sinh `<cleanPhone>@petcare.vn`** vì cột `email` trong `dbo.Users` là `NOT NULL UNIQUE`.
  3. Ở `TC_03`, AI viết `BookingService` trả về `false` khi lỗi, nhưng thực tế `BookingService.bookService` ném `IllegalArgumentException` (khi thú cưng không thuộc khách hàng hoặc dịch vụ ngừng hoạt động) và ném `SQLException` với bảng ánh xạ mã lỗi `FRIENDLY_ERRORS` (`51001`, `51008`, `51009`, `51010`, `51031`, `51032`).
  4. AI chưa tách rõ các giá trị biên của `ValidationUtil` (tiền tố `+84`, độ dài SĐT 9–11 số) và các helper không cần DB (`HomeServlet.buildDefaultServices`, `HomeServlet.buildDefaultReviews`, `AppointmentBalance.getRemainingAmount`).
- **Tài liệu hoàn thiện sau khi sửa:**
  - Đã cập nhật đầy đủ đặc tả đúng dự án PetCare vào `docs/SPECIFICATIONS.md`.
  - Đã hoàn thiện kế hoạch kiểm thử và bảng ma trận 22 ca kiểm thử (bao phủ cả biên form, ngoại lệ và ánh xạ mã lỗi SQL) vào `docs/TEST_PLAN.md` và `docs/TEST_CASES.md`.
