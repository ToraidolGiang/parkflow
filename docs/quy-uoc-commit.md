# Quy ước commit và nhánh

## Thông điệp commit

```
loại(phạm-vi): mô tả ngắn bằng tiếng Việt, không dấu chấm cuối
```

Loại: `feat` (tính năng mới), `fix` (sửa lỗi), `test`, `docs`, `chore` (hạ tầng, cấu hình), `refactor`, `style` (giao diện, định dạng). Phạm vi là gói hoặc màn hình: `ingest`, `forecast`, `plan`, `approval`, `quality`, `student`, `ai`, `sim`, `importer`, `db`, `controller`, `dashboard`, `ui`, ...

Ví dụ tốt: `feat(ingest): ghi sự kiện, chống trùng và cập nhật số xe bãi`. Ví dụ không tốt: `update`, `fix bug`, `done`.

## Một commit là gì

- Một phần việc trọn vẹn, biên dịch được và test xanh (trừ commit `test:` viết test trước).
- Không quá khoảng 30 đến 45 phút làm. Việc lớn tách thành nhiều commit (mô hình, thuật toán, API, test).
- Không commit khóa API, mật khẩu, `.env`. Khóa AI đặt trong `.env` (đã bị `.gitignore` chặn).

## Nhánh và PR

1. `git switch -c feat/<tên>-<mô-tả>` từ `main` mới nhất.
2. Commit nhỏ trên nhánh; trước khi đẩy: `git pull --rebase origin main`.
3. PR vào `main`, mô tả theo mẫu; chờ CI xanh; người khác xem nhanh (5 phút) rồi merge.
4. Merge bằng **"Rebase and merge"** hoặc **"Create a merge commit"**. **Không "Squash and merge"**: squash gộp mọi commit nhỏ thành một và làm mất công của từng người.
5. Không `push --force` lên `main`; không sửa `--date`; không viết lại lịch sử đã đẩy.

## Tài khoản

Mỗi người dùng email GitHub đã xác minh của mình: `git config user.name "<Họ tên>"` và `git config user.email "<email GitHub>"`. Kiểm tra bằng `git log -1 --format='%an <%ae>'` sau commit đầu tiên.
