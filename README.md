# LT_UDM_chat

Ứng dụng chat Java Swing hỗ trợ chat cá nhân, chat nhóm, gửi tệp đa phương
tiện, ghi âm, xem ảnh/video, nghe âm thanh và trạng thái đã nhận/đã đọc.
Tin nhắn được chuyển tiếp qua máy chủ, nên các máy khách có thể ở những mạng
khác nhau nếu cùng kết nối được tới máy chủ.

## Yêu cầu

- JDK 17 trở lên đã cài đặt (`java` và `javac` có trong `PATH`).
- Windows PowerShell.
- Các thư viện cần thiết đã có trong `ChatApp\lib`.

Kiểm tra JDK:

```powershell
java -version
javac -version
```

Các tệp `.jar` trong `ChatApp\lib` là thư viện của ứng dụng, không chạy riêng.
Script tự thêm tất cả thư viện vào classpath khi biên dịch và chạy.

## Chạy lần đầu bằng PowerShell

Mở PowerShell tại thư mục gốc repo (`LT_UDM_chat`).

1. Mở terminal thứ nhất, khởi động server:

   ```powershell
   .\run.ps1 -Mode server
   ```

   Đợi đến khi thấy `Server started on port 8080`. Giữ terminal này mở trong
   lúc sử dụng ứng dụng; nhấn `Ctrl+C` để dừng server.

2. Mở terminal thứ hai, khởi động ứng dụng:

   ```powershell
   .\run.ps1 -Mode app
   ```

   Chọn **Đăng ký** và nhập tên hiển thị, số điện thoại cùng mật khẩu. Số điện
   thoại (đúng 10 chữ số) là định danh duy nhất; tên hiển thị có thể trùng. Ứng
   dụng chưa xác minh quyền sở hữu số điện thoại bằng OTP. Tài khoản và quan
   hệ bạn bè được lưu tập trung trên máy chạy server.

## Dùng trên nhiều máy trong cùng mạng

1. Chạy server trên một máy và giữ cửa sổ server mở.
2. Trên máy chạy server, dùng `ipconfig` để xem địa chỉ IPv4. Cho phép Java
   nhận kết nối TCP cổng `8080` qua Windows Firewall khi được hỏi.
3. Trên mỗi máy khách, mở ứng dụng và nhập địa chỉ IPv4 của máy chủ vào ô
   **Máy chủ** (ví dụ `192.168.1.25`); không nhập `localhost` trên máy khách
   khác. Các máy cần truy cập được nhau trong cùng mạng.
4. Đăng ký/đăng nhập qua địa chỉ máy chủ đó. Tìm tài khoản bằng **Kết bạn**;
   người nhận chấp nhận lời mời thì hai bên mới thấy nhau trong danh sách
   **Bạn bè**.

Nút **Ghi âm** ghi thành tệp WAV rồi gửi vào cuộc trò chuyện. Ảnh có hình xem
trước ngay trong khung chat; video MP4 H.264 có âm thanh AAC phát trong ứng
dụng, nháy đúp video để phóng to toàn màn hình; bản ghi âm WAV có thể phát
trực tiếp. Một số định dạng video khác có thể không được bộ giải mã tích hợp
hỗ trợ. Tệp media có nút tải bản sao xuống máy. Tin nhắn hiển thị trạng thái
**Đã gửi**, **Đã nhận** hoặc **Đã đọc** khi phía nhận gửi xác nhận.

`localhost` chỉ dùng khi ứng dụng và server chạy trên cùng máy. Giao thức hiện
tại chưa mã hóa lưu lượng và mật khẩu chưa được băm an toàn; chỉ dùng trong
mạng tin cậy hoặc qua VPN tin cậy. Không mở cổng này trực tiếp ra Internet.

## Nhắn tin khi các máy ở khác mạng

Cách khuyến nghị là cài Tailscale trên máy chạy server và các máy khách, rồi
đăng nhập chúng vào cùng một mạng Tailscale. Tailscale tạo kết nối riêng được
mã hóa giữa các thiết bị, không cần chuyển tiếp cổng trên router:

1. Khởi động server bằng `.\run.ps1 -Mode server` trên máy luôn bật.
2. Cài Tailscale trên máy server và các máy khách; đăng nhập cùng một mạng
   Tailscale.
3. Trên máy server, lấy địa chỉ Tailscale IPv4 hoặc tên máy Tailscale.
4. Trên mỗi máy khách, nhập địa chỉ đó vào ô **Máy chủ** ở màn hình đăng nhập.
   Địa chỉ `localhost` chỉ dùng trên chính máy server.
5. Nếu Windows Firewall hỏi, cho phép Java nhận kết nối TCP cổng `8080` qua
   giao diện Tailscale. Không tạo quy tắc chuyển tiếp cổng trên router.

Máy server phải đang bật và ứng dụng server phải tiếp tục chạy để chuyển tiếp
tin nhắn và media. Video được phát cục bộ trên thiết bị nhận, không cần webcam.

Không nên dùng địa chỉ IP công khai hoặc chuyển tiếp cổng `8080` trực tiếp:
ứng dụng chưa mã hóa ở tầng ứng dụng và mật khẩu tài khoản chưa được băm an
toàn. Muốn mở dịch vụ công khai cần bổ sung TLS, xác thực kết nối chat và bảo
vệ máy chủ trước.

Script sẽ biên dịch mã Java vào `ChatApp\bin` trước khi chạy. Nếu chỉ muốn
biên dịch mà chưa mở ứng dụng:

```powershell
.\run.ps1 -Mode build
```

## Tạo và dùng nhóm

Server phải đang chạy và cần có ít nhất hai tài khoản. Trong ứng dụng, chọn
**Tạo nhóm**, nhập tên rồi chọn thành viên. Nhóm sẽ hiện ở mục **Nhóm** trong
thanh bên trái; chọn nhóm để mở tin nhắn ở khung bên phải. Nhóm được lưu trong
`ChatApp\data\groups.json`.

Có thể chạy công cụ nhóm cũ bằng:

```powershell
.\run.ps1 -Mode group
```

Tệp đa phương tiện trong cuộc trò chuyện có nút **Tệp đã gửi (n)**; chọn tệp
trong danh sách để mở lại tệp cụ thể.

Trong chat cá nhân và chat nhóm, nút **Biểu tượng** mở bảng chọn có hai thẻ:
**Biểu tượng** chèn emoji vào ô nhập, còn **Nhãn dán** gửi biểu tượng đã chọn
ngay thành một tin nhắn. Ứng dụng dùng ảnh emoji có sẵn trong
`ChatApp\resources\emoji` để hiển thị trong nội dung chat và lịch sử, tránh
phụ thuộc vào font emoji trên máy nhận.

Khi đang ở màn hình chính, ứng dụng giữ kết nối với server để nhận tin nhắn
riêng và nhóm; tin đến được lưu vào lịch sử để mở xem sau. Khi mở một cuộc trò
chuyện, cửa sổ chat tiếp quản kết nối; khi đóng cửa sổ, kết nối màn hình chính
được khôi phục.

## Các chế độ của `run.ps1`

| Lệnh | Tác dụng |
| --- | --- |
| `.\run.ps1 -Mode build` | Biên dịch ứng dụng, không mở cửa sổ |
| `.\run.ps1 -Mode server` | Khởi chạy server tại cổng 8080 |
| `.\run.ps1 -Mode app` | Mở màn hình đăng nhập và giao diện chính |
| `.\run.ps1 -Mode chat` | Mở nhanh chat cá nhân để thử nghiệm |
| `.\run.ps1 -Mode group` | Mở công cụ tạo/mở chat nhóm |
| `.\run.ps1 -Mode media-demo` | Mở cửa sổ thử gửi media độc lập |

Mọi chế độ đều tự biên dịch trước khi chạy và tự nạp các thư viện cần thiết.

## Mở từ VS Code

Mở thư mục gốc repo trong VS Code. Workspace đã cấu hình source path và
mọi thư viện trong `ChatApp\lib` qua `.vscode\settings.json`. Các lớp chạy chính:

- `server.ServerMain` — server, chạy lớp này trước.
- `auth.LoginUI` — đăng nhập, sau đó mở giao diện chính.
- `chat.ChatHomeUI` — tìm người, tạo nhóm và mở cuộc trò chuyện.
- `chat.ChatUI` — cửa sổ chat cá nhân.
- `group.GroupChatUI` — cửa sổ chat nhóm.

Nếu VS Code vẫn báo `The import com.google.gson cannot be resolved`, mở
Command Palette và chạy **Java: Clean Java Language Server Workspace**, sau đó
reload VS Code.

## Dữ liệu và tệp đa phương tiện

- Dữ liệu người dùng, nhóm và lịch sử chat nằm trong `ChatApp\data`.
- Media tải về nằm trong `ChatApp\media` khi chạy từ thư mục gốc repo hoặc từ
  thư mục `ChatApp`.
- Mỗi file gửi tối đa 50 MB.
