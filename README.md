# And-ViewerImageView
タッチで動かせるImageView

ユーザーがタッチ操作で移動・拡大縮小できるImageView.
とりあえず普通にImageViewとして画面に出すだけで 操作できるものが出ます.

移動・ズームには ImageView の imageMatrixを使っているので 現在のスクロール位置・ズーム率は getImageMatrix() から取得できます.
