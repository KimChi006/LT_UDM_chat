@echo off

mkdir ChatApp

cd ChatApp

mkdir src
mkdir src\auth
mkdir src\chat
mkdir src\group
mkdir src\media
mkdir src\history
mkdir src\status
mkdir src\emoji
mkdir src\storage
mkdir src\model
mkdir src\server

mkdir data

mkdir media
mkdir media\files
mkdir media\images
mkdir media\videos
mkdir media\sounds

mkdir resources
mkdir resources\emoji
mkdir resources\icons

mkdir docs

type nul > src\auth\LoginUI.java
type nul > src\auth\RegisterUI.java
type nul > src\auth\UserSession.java

type nul > src\chat\ChatUI.java
type nul > src\chat\ClientSocket.java
type nul > src\chat\MessageListener.java
type nul > src\chat\Message.java

type nul > src\group\GroupChatUI.java
type nul > src\group\GroupManager.java
type nul > src\group\Group.java

type nul > src\media\FileTransferClient.java
type nul > src\media\FileTransferServer.java
type nul > src\media\FileInfo.java
type nul > src\media\MediaManager.java

type nul > src\history\HistoryManager.java
type nul > src\history\HistoryUI.java

type nul > src\status\StatusManager.java
type nul > src\status\UserListUI.java

type nul > src\emoji\EmojiPanel.java

type nul > src\storage\JsonStorage.java
type nul > src\storage\UserStorage.java
type nul > src\storage\MessageStorage.java
type nul > src\storage\GroupStorage.java

type nul > src\model\User.java
type nul > src\model\PacketType.java
type nul > src\model\ChatConstants.java

type nul > src\server\ServerMain.java
type nul > src\server\ClientHandler.java
type nul > src\server\MessageRouter.java

echo [] > data\users.json
echo [] > data\messages.json
echo [] > data\groups.json
echo {} > data\status.json

type nul > docs\PHAN_CHIA_CONG_VIEC.md
type nul > docs\API_PACKET_DESIGN.md
type nul > README.md

echo Project created successfully!
pause