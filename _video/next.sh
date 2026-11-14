echo START $(date)
set -e
cd /Users/dmitrij/prog/vadim_na_trone/_video
export PATH="$PWD/.node/bin:$PATH"
mkdir -p out
for f in 45 150 300 380 460 500 640 820 880 1000 1080; do
  npx remotion still src/index.ts News out/still_$f.png --frame=$f --log=error
done
echo "== stills ok"
npx remotion render src/index.ts News out/news_preview.mp4 --codec=h264 --crf=20 --log=info
echo DONE
