# Plan 14: Remember the last request tab per endpoint

## Mục tiêu

Mỗi API phải ghi nhớ request tab mà người dùng truy cập gần nhất.

Ví dụ:

1. Mở API A và chọn `Params`.
2. Mở API B và chọn `Body`.
3. Quay lại API A: giao diện phải mở lại `Params`.
4. Quay lại API B: giao diện phải mở lại `Body`.

Với API chưa từng được mở hoặc chưa có tab được lưu, mặc định phải là
`Params`.

Phạm vi của tính năng này là năm request tab hiện có:

- `Params`
- `Headers`
- `Cookies`
- `Auth`
- `Body`

Việc ghi nhớ request tab độc lập với lựa chọn `JSON`/`Form-Data` bên trong tab
`Body`. Body type tiếp tục được lưu bằng `RequestBodyType` như hiện tại.

## Kết quả phân tích nguyên nhân

`EndpointDetailPanel` chỉ tạo một `JBTabbedPane requestTabs` dùng chung cho mọi
endpoint. Swing giữ nguyên `selectedIndex` của component này cho đến khi code
chủ động thay đổi nó.

Trong `displayEndpoint(...)`, SpringLens hiện thực hiện các việc sau:

- lưu dữ liệu của endpoint cũ qua `collectDataToModel()`;
- thay `currentEndpoint`;
- nạp params, headers, cookies, auth, request body và response của endpoint mới;
- chọn đúng `JSON` hoặc `Form-Data` bên trong tab Body.

Tuy nhiên, method này không đọc hoặc đặt lại tab cấp cao của `requestTabs`.
Vì vậy nếu component đang đứng ở `Body`, khi chọn API khác nó vẫn tiếp tục đứng
ở `Body`. Đây không phải hành vi ngẫu nhiên; nó là state toàn cục của một Swing
component đang bị dùng thay cho state riêng của từng endpoint.

`requestTabs.setSelectedIndex(0)` hiện chỉ được gọi khi gửi request còn thiếu
path variable. Không có logic mặc định/restore tab khi chuyển endpoint.

## Thiết kế đề xuất

### 1. Biểu diễn tab bằng enum ổn định

Thêm enum `RequestTab` trong package `model`:

```java
public enum RequestTab {
    PARAMS,
    HEADERS,
    COOKIES,
    AUTH,
    BODY
}
```

Không lưu trực tiếp Swing tab index (`0`, `1`, `2`...). Index phụ thuộc vào thứ
tự UI và sẽ trở nên sai nếu sau này chèn thêm tab. Enum thể hiện ý nghĩa nghiệp
vụ, dễ migrate và dễ test hơn.

Nếu cần chuyển đổi giữa enum và Swing index, đặt mapping tập trung trong
`EndpointDetailPanel` hoặc một helper nhỏ:

```java
private int requestTabIndex(RequestTab tab) {
    return switch (tab) {
        case PARAMS -> 0;
        case HEADERS -> 1;
        case COOKIES -> 2;
        case AUTH -> 3;
        case BODY -> 4;
    };
}
```

Chiều ngược lại cũng phải có một mapping duy nhất từ index sang enum. Mọi index
không hợp lệ phải fallback về `PARAMS`.

### 2. Lưu tab gần nhất trong `EndpointModel`

Thêm field:

```java
private RequestTab selectedRequestTab = RequestTab.PARAMS;
```

Getter/setter phải chuẩn hoá `null` về `PARAMS`. Nhờ vậy endpoint mới, endpoint
không có body và dữ liệu legacy đều có cùng một default rõ ràng.

State này thuộc về endpoint, không thuộc về `EndpointDetailPanel`, vì cùng một
panel đang lần lượt hiển thị nhiều endpoint.

### 3. Cập nhật model khi người dùng đổi tab

Đăng ký `ChangeListener` cho `requestTabs`. Khi người dùng chọn tab:

1. Chuyển `selectedIndex` sang `RequestTab`.
2. Gán tab đó cho `currentEndpoint`.
3. Bỏ qua listener khi `isUpdatingUI == true` hoặc `currentEndpoint == null`.

Guard `isUpdatingUI` là bắt buộc. Khi `displayEndpoint(...)` khôi phục tab của
API B bằng code, Swing có thể phát change event. Nếu không có guard, event đó có
thể ghi nhầm UI state đang restore như một thao tác mới của người dùng.

`collectDataToModel()` cũng nên đọc `requestTabs.getSelectedIndex()` và cập nhật
`currentEndpoint` trước khi gọi `state.saveEndpoint(...)`. Đây là lớp an toàn
cuối cùng bảo đảm tab của API cũ được chụp đúng ngay trước khi chuyển sang API
mới.

Không gọi toàn bộ `saveEndpoint(...)` trực tiếp ở mỗi change event. Method này
lưu nhiều dữ liệu khác và hiện còn quản lý response history; gọi nó chỉ vì đổi
tab có thể tạo ghi state thừa hoặc duplicate history. Việc lưu sẽ diễn ra theo
luồng `collectDataToModel()` sẵn có khi chuyển endpoint, gửi request hoặc export.

### 4. Khôi phục đúng tab trong `displayEndpoint(...)`

Sau khi gán `this.currentEndpoint = endpoint` và bật `isUpdatingUI`, chọn request
tab từ model:

```java
RequestTab selectedTab = endpoint.getSelectedRequestTab();
requestTabs.setSelectedIndex(requestTabIndex(selectedTab));
```

Thao tác này phải chạy cho mọi scanned endpoint và manual endpoint.

Khi `displayEndpoint(null)` được gọi, reset `requestTabs` về `Params` để panel
rỗng không giữ hình ảnh UI của endpoint vừa đóng. Việc reset này không được ghi
đè tab đã lưu của endpoint cũ.

Thứ tự xử lý khi chuyển API phải là:

1. Chụp dữ liệu và selected tab của endpoint cũ.
2. Lưu endpoint cũ.
3. Gán endpoint mới vào `currentEndpoint`.
4. Bật `isUpdatingUI`.
5. Nạp dữ liệu và selected tab của endpoint mới.
6. Tắt `isUpdatingUI`.

### 5. Persist tab theo từng endpoint

Thêm vào `EndpointSavedState`:

```java
public RequestTab selectedRequestTab = RequestTab.PARAMS;
```

Cập nhật `SpringLensState`:

- `saveEndpoint(...)`: copy `endpoint.getSelectedRequestTab()` sang saved state;
- `restoreEndpoint(...)`: copy saved tab về model;
- nếu saved value là `null`, không hợp lệ hoặc đến từ state cũ chưa có field,
  fallback về `PARAMS`;
- áp dụng giống nhau cho scanned endpoint và manual endpoint.

Không cần tăng schema version chỉ để thêm field có default tương thích ngược.
IntelliJ state cũ không có field này sẽ đọc thành default/null và được chuẩn hoá
về `PARAMS`. Chỉ tăng schema version nếu framework serializer thực tế không áp
dụng field initializer khi đọc XML cũ; trường hợp đó phải được xác nhận bằng
test migration trước.

Việc persist giúp UX vẫn đúng sau reload endpoint, reload project hoặc restart
IDE, thay vì chỉ hoạt động trong một phiên mở Tool Window.

## Các file dự kiến thay đổi

- `model/RequestTab.java`: enum biểu diễn tab theo ý nghĩa ổn định.
- `model/EndpointModel.java`: giữ `selectedRequestTab`, default `PARAMS` và
  null-safe setter.
- `state/EndpointSavedState.java`: persist tab gần nhất theo endpoint.
- `state/SpringLensState.java`: save/restore tab cho scanned và manual endpoint.
- `ui/EndpointDetailPanel.java`: lắng nghe tab change, chụp tab endpoint cũ và
  restore tab endpoint mới.
- Các test tương ứng dưới `src/test/java`.

## Kế hoạch kiểm thử

### Unit test model và mapping

1. `EndpointModel` mới có `selectedRequestTab == PARAMS`.
2. Setter nhận `null` fallback về `PARAMS`.
3. Mapping enum/index đúng cho đủ năm tab.
4. Index âm hoặc vượt số lượng tab fallback về `PARAMS`.

### State persistence test

1. Tạo API A lưu `PARAMS`, API B lưu `BODY`.
2. Restore vào hai `EndpointModel` mới.
3. Xác nhận A vẫn là `PARAMS`, B vẫn là `BODY`.
4. Xác nhận state legacy không có selected tab restore thành `PARAMS`.
5. Chạy cùng kịch bản với manual endpoint.
6. Xác nhận lưu tab không làm thay đổi body type, request body, params, auth,
   headers hoặc response history.

### UI regression test

Với IntelliJ UI fixture hoặc một selection coordinator được tách riêng để test:

1. Hiển thị API A lần đầu: tab `Params` được chọn.
2. Chọn `Params` ở A, chuyển sang B, chọn `Body`.
3. Chuyển về A: `Params` được chọn.
4. Chuyển về B: `Body` được chọn.
5. Hiển thị API C chưa từng mở: `Params` được chọn, không kế thừa tab của B.
6. Gọi `displayEndpoint(null)`: panel rỗng trở về `Params`, nhưng tab đã lưu của
   API trước không bị thay đổi.
7. Reload/rescan endpoint rồi restore state: tab gần nhất vẫn đúng.
8. Khi validation thiếu path variable tự chuyển sang `Params`, model của endpoint
   hiện tại cũng ghi nhận `Params` là tab gần nhất.

## Kiểm thử thủ công

1. Mở một scanned API mới và xác nhận mặc định ở `Params`.
2. Chọn `Headers`, chuyển sang API khác rồi quay lại; API đầu phải mở `Headers`.
3. Ở API thứ hai chọn `Body`, qua lại giữa hai API nhiều lần; mỗi API phải giữ
   tab riêng.
4. Trong `Body`, chọn Form-Data; chuyển API rồi quay lại và xác nhận đồng thời
   khôi phục tab `Body` lẫn body type Form-Data.
5. Thử với manual request trong collection.
6. Bấm Reload, đóng/mở project hoặc restart IDE và xác nhận tab đã lưu vẫn được
   khôi phục.

Chạy kiểm thử tự động:

```bash
./gradlew test
./gradlew buildPlugin
```

## Acceptance criteria

- API chưa từng có selected-tab state luôn mở ở `Params`.
- Mỗi API ghi nhớ độc lập tab gần nhất trong năm request tab.
- Chuyển qua lại giữa API không còn kế thừa tab của API vừa xem.
- State hoạt động cho cả scanned endpoint và manual endpoint.
- Reload/restart không làm mất selected tab đã lưu.
- Việc restore tab không phát sinh save event ngoài ý muốn hoặc ghi nhầm sang
  endpoint khác.
- Lựa chọn `JSON`/`Form-Data` bên trong `Body` vẫn hoạt động và được lưu độc lập.
- Request data, auth, response cache và response history không thay đổi hành vi.

## Không nằm trong phạm vi

- Ghi nhớ `Response Body` hay `Response Headers` ở khu vực response.
- Ghi nhớ vị trí con trỏ, scroll position hoặc focus control bên trong từng tab.
- Thay đổi thứ tự hoặc nội dung năm request tab hiện có.
