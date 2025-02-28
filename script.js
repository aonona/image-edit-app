/* 
  script.js

  - アップロードした画像をキャンバスに表示
  - Pointer Eventsで範囲選択（赤枠）
  - 選択部分にモザイク、トリミング
  - Undo、Reset、Download機能
  - PCでもスマホでも動作
*/

const uploadInput = document.getElementById('upload');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

// 操作用ボタン
const applyMosaicBtn = document.getElementById('applyMosaic');
const applyTrimBtn = document.getElementById('applyTrim');
const downloadImageBtn = document.getElementById('downloadImage');
const undoButton = document.getElementById('undoButton');
const resetButton = document.getElementById('resetButton');

// 画像関連の変数
let img = new Image();
let isImageLoaded = false;

// 選択範囲関係
let startX, startY, endX, endY;
let isSelecting = false;

// 「最新の編集後の状態」を常に保持 (赤枠は含まない)
let editedImageData = null;
// 「オリジナル状態」のデータ (リセット時に戻すため)
let originalImageData = null;
// Undo用の履歴スタック (配列末尾を最新とする)
let historyStack = [];

/* ---------------------------------
   1. 画像アップロード処理
----------------------------------- */
uploadInput.addEventListener('change', (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  const reader = new FileReader();
  reader.onload = function(e) {
      img.onload = function() {
          // キャンバスを画像サイズに合わせる
          canvas.width = img.width;
          canvas.height = img.height;
          // 画像を描画
          ctx.drawImage(img, 0, 0);
          
          isImageLoaded = true;
          
          // 読み込んだ直後の状態を保存
          editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
          originalImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

          // ヒストリを初期化
          historyStack = [];
      };
      img.src = e.target.result;
  };
  reader.readAsDataURL(file);
});

/* ---------------------------------
   2. Pointer Eventsによる範囲選択
      (スマホ & PC 両対応)
----------------------------------- */
canvas.addEventListener('pointerdown', (event) => {
  if (!isImageLoaded) return;
  
  // キャンバスのCSS表示領域と実際の描画解像度のズレを補正するためのスケーリング計算
  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width  / rect.width;
  const scaleY = canvas.height / rect.height;

  // スケーリング後の実際の座標を算出
  startX = (event.clientX - rect.left) * scaleX;
  startY = (event.clientY - rect.top)  * scaleY;
  
  isSelecting = true;
});

canvas.addEventListener('pointermove', (event) => {
  if (!isSelecting) return;
  
  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width  / rect.width;
  const scaleY = canvas.height / rect.height;

  endX = (event.clientX - rect.left) * scaleX;
  endY = (event.clientY - rect.top)  * scaleY;
  
  drawSelection();
});

canvas.addEventListener('pointerup', (event) => {
  if (!isSelecting) return;
  
  const rect = canvas.getBoundingClientRect();
  const scaleX = canvas.width  / rect.width;
  const scaleY = canvas.height / rect.height;

  endX = (event.clientX - rect.left) * scaleX;
  endY = (event.clientY - rect.top)  * scaleY;
  
  isSelecting = false;
  drawSelection();
});

// タッチ操作が中断された場合
canvas.addEventListener('pointercancel', () => {
  isSelecting = false;
});

/* ---------------------------------
   3. 赤枠描画関数
----------------------------------- */
function drawSelection() {
  if (!isImageLoaded) return;

  // まず編集後の画像データを再描画(赤枠が含まれない状態)
  ctx.putImageData(editedImageData, 0, 0);

  if (
    startX === undefined ||
    startY === undefined ||
    endX   === undefined ||
    endY   === undefined
  ) {
    return;
  }

  // 赤い選択枠を描画
  ctx.strokeStyle = 'red';
  ctx.lineWidth = 2;
  ctx.strokeRect(startX, startY, endX - startX, endY - startY);
}

/* ---------------------------------
   4. モザイク処理
----------------------------------- */
applyMosaicBtn.addEventListener('click', () => {
  if (!isImageLoaded || startX === undefined || endX === undefined) return;

  // (1) 現在のeditedImageDataを履歴に追加
  historyStack.push(editedImageData);

  // (2) 赤枠を消して元の状態に戻す
  ctx.putImageData(editedImageData, 0, 0);

  // (3) キャンバス全体の画像を取得
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const blockSize = 10;

  // (4) 選択範囲を確定（0～canvas.width/height内にクランプ）
  let startXInt = Math.min(startX, endX);
  let endXInt   = Math.max(startX, endX);
  let startYInt = Math.min(startY, endY);
  let endYInt   = Math.max(startY, endY);

  // はみ出し防止
  startXInt = Math.max(0, Math.min(canvas.width,  startXInt));
  endXInt   = Math.max(0, Math.min(canvas.width,  endXInt));
  startYInt = Math.max(0, Math.min(canvas.height, startYInt));
  endYInt   = Math.max(0, Math.min(canvas.height, endYInt));

  // (5) ブロック単位でモザイクをかける
  for (let y = startYInt; y < endYInt; y += blockSize) {
    for (let x = startXInt; x < endXInt; x += blockSize) {
      let r = 0, g = 0, b = 0, count = 0;
      
      // ブロック内のピクセルを走査
      for (let dy = 0; dy < blockSize && (y + dy) < endYInt; dy++) {
        for (let dx = 0; dx < blockSize && (x + dx) < endXInt; dx++) {
          const idx = ((y + dy) * canvas.width + (x + dx)) * 4;
          r += imageData.data[idx];
          g += imageData.data[idx + 1];
          b += imageData.data[idx + 2];
          count++;
        }
      }
      
      const avgR = Math.floor(r / count);
      const avgG = Math.floor(g / count);
      const avgB = Math.floor(b / count);

      // ブロック内を平均色で塗りつぶす
      for (let dy = 0; dy < blockSize && (y + dy) < endYInt; dy++) {
        for (let dx = 0; dx < blockSize && (x + dx) < endXInt; dx++) {
          const idx = ((y + dy) * canvas.width + (x + dx)) * 4;
          imageData.data[idx]     = avgR;
          imageData.data[idx + 1] = avgG;
          imageData.data[idx + 2] = avgB;
        }
      }
    }
  }

  // (6) モザイク結果を反映
  ctx.putImageData(imageData, 0, 0);

  // (7) editedImageDataを更新（赤枠なし状態）
  editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

  // (8) 必要に応じて赤枠を再描画
  drawSelection();
});

/* ---------------------------------
   5. トリミング処理
----------------------------------- */
applyTrimBtn.addEventListener('click', () => {
  if (!isImageLoaded ||
      startX === undefined || startY === undefined ||
      endX   === undefined || endY   === undefined
  ) {
    return;
  }

  // (1) 現在のeditedImageDataを履歴に追加
  historyStack.push(editedImageData);

  // (2) 赤枠を消して元の状態に戻す
  ctx.putImageData(editedImageData, 0, 0);

  // (3) トリミング範囲を確定（クランプ）
  let trimX = Math.min(startX, endX);
  let trimY = Math.min(startY, endY);
  let trimWidth  = Math.abs(endX - startX);
  let trimHeight = Math.abs(endY - startY);

  trimX = Math.max(0, Math.min(canvas.width,  trimX));
  trimY = Math.max(0, Math.min(canvas.height, trimY));

  if (trimX + trimWidth  > canvas.width)  trimWidth  = canvas.width  - trimX;
  if (trimY + trimHeight > canvas.height) trimHeight = canvas.height - trimY;

  // (4) 選択範囲のイメージを取得
  const trimmedData = ctx.getImageData(trimX, trimY, trimWidth, trimHeight);

  // (5) キャンバスをトリミングしたサイズに変更して描画
  canvas.width = trimWidth;
  canvas.height = trimHeight;
  ctx.putImageData(trimmedData, 0, 0);

  // (6) editedImageDataを更新
  editedImageData = ctx.getImageData(0, 0, canvas.width, canvas.height);

  // (7) 選択範囲リセット
  startX = startY = endX = endY = undefined;
});

/* ---------------------------------
   6. ダウンロード機能
----------------------------------- */
downloadImageBtn.addEventListener('click', () => {
  if (!isImageLoaded) return;

  // 赤枠を消しておく
  ctx.putImageData(editedImageData, 0, 0);

  // ダウンロード用リンクを作成してクリック
  const link = document.createElement('a');
  link.download = 'edited-image.png';
  link.href = canvas.toDataURL();
  link.click();
});

/* ---------------------------------
   7. 一個前に戻す (Undo)
----------------------------------- */
undoButton.addEventListener('click', () => {
  if (historyStack.length === 0) return;

  // (1) 履歴から最後の状態を取り出す
  editedImageData = historyStack.pop();

  // (2) キャンバスサイズを復元して再描画
  canvas.width = editedImageData.width;
  canvas.height = editedImageData.height;
  ctx.putImageData(editedImageData, 0, 0);

  // (3) 選択範囲リセット
  startX = startY = endX = endY = undefined;
});

/* ---------------------------------
   8. リセット機能
----------------------------------- */
resetButton.addEventListener('click', () => {
  if (!originalImageData) return;

  // オリジナルの状態を復元
  editedImageData = originalImageData;

  // キャンバスサイズも元に戻す
  canvas.width = originalImageData.width;
  canvas.height = originalImageData.height;

  // 再描画
  ctx.putImageData(originalImageData, 0, 0);

  // 履歴をクリア
  historyStack = [];

  // 選択範囲リセット
  startX = startY = endX = endY = undefined;
});
