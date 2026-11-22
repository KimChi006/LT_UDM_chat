CODING RULES

1. Package
- Viết thường toàn bộ.
- Tên package phải trùng tên thư mục.

Ví dụ:
-package server;
-package chat;
-package group;
-package media;
-package history;
-package storage;
-package auth;
-package status;
-package emoji;

2. Class
-Viết hoa chữ cái đầu mỗi từ.

Ví dụ:
public class ServerMain {
}
public class ClientHandler {
}

3. Biến
- Viết thường toàn bộ.
- Không dùng dấu gạch dưới (_).

Ví dụ:

String username;
String password;
String status;

String sender;
String receiver;
String content;
String filepath;

String groupname;
String messagetype;

4. Hàm
- Viết thường chữ cái đầu.
- Nếu nhiều từ thì viết hoa chữ cái đầu từ thứ hai.

Ví dụ:
-sendMessage();
-loadHistory();
-saveMessage();
-createGroup();

Không dùng:

SendMessage();
send_message();

5. Hằng số
- Viết hoa toàn bộ.
- Nhiều từ dùng dấu gạch dưới.

Ví dụ:
public static final int SERVER_PORT = 8080;
public static final int MAX_CLIENT = 100;
public static final String SERVER_HOST = "localhost";

6. Enum
- Viết hoa toàn bộ.
- Nhiều từ dùng dấu gạch dưới.

Ví dụ:

public enum PacketType {
    LOGIN,
    REGISTER,
    MESSAGE,
    GROUP_MESSAGE,
    FILE,
    IMAGE,
    VIDEO,
    SOUND,
    STATUS,
    EMOJI
}

7. Import
- Khi sử dụng class từ package khác phải import.
Ví dụ:

package chat;

import model.PacketType;
package server;

import model.PacketType;

8. JSON
- Key JSON viết thường toàn bộ.

Ví dụ:

{
  "sender": "chi",
  "receiver": "nam",
  "content": "hello",
  "filepath": "media/files/test.pdf",
  "time": "10:30"
}

9. Ngôn ngữ
Toàn bộ code sử dụng tiếng Anh.
Không đặt tên tiếng Việt.
Đúng:

username
sendMessage()
createGroup()

10. PacketType chung cho toàn nhóm

LOGIN
REGISTER
MESSAGE
GROUP_MESSAGE
FILE
IMAGE
VIDEO
SOUND
STATUS
EMOJI


Không tự ý đổi tên hoặc thêm PacketType khi chưa thống nhất với nhóm.