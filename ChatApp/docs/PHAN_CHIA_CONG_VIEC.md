PHÂN CHIA CÔNG VIỆC - ĐỒ ÁN CHAT APP
Công nghệ sử dụng
Java
Java Socket
JSON Storage
Java Swing
Git + GitHub

--------------------------------------------------------------------------------

Thành viên 1 - SERVER CORE
Thư mục phụ trách
src/server
File
ServerMain.java
ClientHandler.java
MessageRouter.java

Nhiệm vụ
Khởi tạo Server Socket.
Quản lý kết nối nhiều Client.
Tạo luồng xử lý cho mỗi Client.
Điều hướng (route) các loại packet.
Chuyển tiếp tin nhắn giữa các Client.
Hỗ trợ Group Chat thông qua broadcast.

Kết quả mong đợi
Server chạy ổn định.
Nhiều Client có thể kết nối đồng thời.
Các module khác có thể gửi packet thông qua Server.

--------------------------------------------------------------------------------

Thành viên 2 - CHAT & GROUP CHAT
Thư mục phụ trách
src/chat
src/group
File
ChatUI.java
ClientSocket.java
MessageListener.java
Message.java

Group.java
GroupManager.java
GroupChatUI.java

Nhiệm vụ
P2P Chat
Gửi tin nhắn cá nhân.
Nhận tin nhắn cá nhân.
Hiển thị người gửi.
Hiển thị thời gian gửi.
Group Chat
Tạo nhóm.
Thêm thành viên vào nhóm.
Gửi tin nhắn nhóm.
Hiển thị tin nhắn nhóm.

Kết quả mong đợi
User A chat được với User B.
Tạo nhóm và chat nhóm được.

--------------------------------------------------------------------------------

Thành viên 3 - MEDIA
Thư mục phụ trách
src/media

media/files
media/images
media/videos
media/sounds
File
FileInfo.java
FileTransferClient.java
FileTransferServer.java
MediaManager.java

Nhiệm vụ
File
Gửi file bất kỳ.
Image
Gửi ảnh.
Hiển thị ảnh nhận được.
Video
Gửi video.
Sound
Gửi file âm thanh.


Kết quả mong đợi
Truyền file thành công.
Truyền ảnh thành công.
Truyền video thành công.
Truyền âm thanh thành công.

--------------------------------------------------------------------------------

Thành viên 4 - HISTORY & STORAGE
Thư mục phụ trách
src/history
src/storage

data/
File
HistoryManager.java
HistoryUI.java

JsonStorage.java
UserStorage.java
MessageStorage.java
GroupStorage.java

Dữ liệu quản lý
data/users.json
data/messages.json
data/groups.json
data/status.json

Nhiệm vụ
Storage
Đọc dữ liệu JSON.
Ghi dữ liệu JSON.
Quản lý cấu trúc dữ liệu.
History
Lưu tin nhắn.
Đọc lịch sử chat.
Tải lại lịch sử khi mở cuộc trò chuyện.


Kết quả mong đợi
Dữ liệu không mất khi tắt chương trình.
Có thể xem lại lịch sử tin nhắn.

--------------------------------------------------------------------------------

Thành viên 5 - UI, LOGIN, EMOJI, STATUS
Thư mục phụ trách
src/auth
src/status
src/emoji

resources/
File
LoginUI.java
RegisterUI.java
UserSession.java

StatusManager.java
UserListUI.java

EmojiPanel.java
Tài nguyên
resources/emoji
resources/icons

Nhiệm vụ
Login
Đăng nhập.
Đăng ký.
Status
Online.
Offline.
Danh sách người dùng.
Emoji
Chọn emoji.
Gửi emoji trong tin nhắn.
UI
Thiết kế giao diện.
Đồng bộ giao diện giữa các module.
Kết quả mong đợi
Giao diện thống nhất.
Người dùng đăng nhập được.
Hiển thị trạng thái online/offline.

--------------------------------------------------------------------------------

QUY ƯỚC CHUNG
Branch Git
Mỗi thành viên làm trên branch riêng:

feature/server-core
feature/chat-group
feature/media
feature/history-storage
feature/ui-auth-status
Commit
Ví dụ:

git commit -m "Add group chat UI"
git commit -m "Implement file transfer"
git commit -m "Add history manager"
Không sửa file của người khác nếu chưa trao đổi
Ví dụ:

TV1 không sửa src/media
TV3 không sửa src/server
TV5 không sửa src/storage
Tránh conflict khi merge.