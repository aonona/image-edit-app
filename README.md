# image-edit-app
これが「アップロード → 選択 → モザイク or トリミング → Undo → Reset → ダウンロード」まで完備した完全コード
HTML と script.js は同じフォルダに置いてください (例: index.html / script.js)。
Pointer Events でマウスもタッチ操作も共通化されているので、PCでもスマホでも動きます。
スケーリング計算 (scaleX, scaleY) を使っているので、キャンバスの見た目サイズと内部解像度が異なる場合でも座標がずれません。
モザイクやトリミングは選択範囲内だけに適用されます。
Undo (一個前に戻る): 直前の editedImageData をスタックに積む形で実装しています。
Reset: 画像読み込み直後の originalImageData を復元。
Download: キャンバスの画像を赤枠なしでダウンロード。
