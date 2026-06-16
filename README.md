# Pencatatan Keuangan Lokal

Aplikasi Android Kotlin untuk mencatat transaksi dari kalimat bebas, misalnya `investasi 100 rb di bibit` atau `beli makan 10 ribu di warteg`.

Semua berjalan lokal di perangkat:

- Tidak ada permission internet.
- Catatan disimpan di `SharedPreferences` perangkat.
- Model kategori disimpan statis di `app/src/main/assets/category_model.json`.
- Training model dilakukan di komputer dengan Python, bukan di handphone.

## Kategori

- Makan & Minum
- Transport
- Belanja
- Tagihan
- Hiburan
- Gaji/Pemasukan
- Kesehatan
- Transfer

## Build Android

Buka folder ini di Android Studio, lalu jalankan konfigurasi `app`.

Jika Gradle sudah tersedia di terminal:

```powershell
gradle :app:assembleDebug
```

## Training Ulang Model

Dataset ada di `ml/training_data.csv`. Edit atau tambah contoh kalimat di sana, lalu jalankan:

```powershell
python ml/train_category_model.py
```

Script akan membuat ulang:

```text
app/src/main/assets/category_model.json
```

Setelah file model masuk ke asset aplikasi, aplikasi tetap bisa berjalan offline di handphone.

