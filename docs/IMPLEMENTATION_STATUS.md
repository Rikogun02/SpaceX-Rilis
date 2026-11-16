# SpaceX Rilis — status implementasi

- Penemuan kandidat token Solana memakai DexScreener latest token profiles.
- Pair pasar diperkaya dari endpoint token pairs DexScreener.
- Polling REST 30 detik saat aplikasi terbuka.
- PumpPortal WebSocket best-effort dengan reconnect backoff; polling REST menjadi jalur fallback.
- Skor heuristik transparan berdasarkan likuiditas, volume 1 jam, rasio beli/jual 5 menit dan perubahan harga 5 menit.
- RugCheck summary diminta sesuai kebutuhan; data gagal/tidak ada selalu dianggap belum terverifikasi.
- Simulasi posisi dan saldo disimpan lokal di perangkat.

## Batasan yang harus diketahui
- DexScreener latest profiles bukan feed lengkap Pump.fun; token sangat baru bisa belum punya pair.
- PumpPortal event stream saat ini menunjukkan konektivitas; pengayaan token dilakukan melalui polling DexScreener.
- Skor bukan probabilitas profit, bukan skor RugCheck, dan belum dikalibrasi terhadap histori hasil.
- Holder concentration, wallet clustering, riwayat creator, LP lock/burn, serta bundle detection belum diverifikasi pada build ini.
- Polling 30 detik hanya berjalan selama aplikasi aktif; Android dapat menangguhkan aplikasi di background.
- Posisi simulasi tersimpan lokal dan belum dihitung ulang mark-to-market otomatis.

## Keamanan
Simulation only. Tidak ada seed phrase/private key, signing, swap, atau jalur live-buy.

## Upstream API references
- DexScreener: https://docs.dexscreener.com/api/reference
- PumpPortal real-time data: https://pumpportal.fun/data-api/real-time/
- RugCheck summary endpoint reference: https://fluxrpc.com/docs/rugcheck/getTokenReportSummary
