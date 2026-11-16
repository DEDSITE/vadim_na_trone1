# Рендер текстур комнаты 2 из HTML (стиль макета Claude Design) → PNG, альфа бинарная.
import sys, json, asyncio
from playwright.async_api import async_playwright
from PIL import Image
F='/home/claude/w/proj/src/client/resources/assets/vadim_na_trone/font/'
CSS=f"""@font-face{{font-family:S;src:url(file://{F}pt_sans.ttf)}}
@font-face{{font-family:S;font-weight:700;src:url(file://{F}pt_sans_bold.ttf)}}
@font-face{{font-family:M;src:url(file://{F}pt_mono.ttf)}}
*{{margin:0;padding:0;box-sizing:border-box}} body{{background:transparent}}
.b{{font-family:S;font-weight:700;transform-origin:left top}}"""
async def main(jobs, out):
    async with async_playwright() as p:
        br = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox'])
        pg = await br.new_page(device_scale_factor=1)
        for name,(w,h,html,cut) in jobs.items():
            await pg.set_viewport_size({'width':w,'height':h})
            open(f'{out}/_t.html','w').write(f"<html><head><meta charset='utf-8'><style>{CSS}</style></head><body style='width:{w}px;height:{h}px;overflow:hidden;position:relative'>{html}</body></html>")
            await pg.goto(f'file://{out}/_t.html')
            await pg.evaluate('document.fonts.ready')
            await pg.wait_for_timeout(120)
            await pg.screenshot(path=f'{out}/{name}.png', omit_background=True)
            im=Image.open(f'{out}/{name}.png').convert('RGBA')
            if cut:
                px=im.load()
                for y in range(im.height):
                    for x in range(im.width):
                        r,g,b,a=px[x,y]; px[x,y]=(r,g,b,255 if a>110 else 0)
            im.save(f'{out}/{name}.png')
            print(name, im.size)
        await br.close()
if __name__=='__main__':
    import os; jobs=json.load(open(sys.argv[1])); asyncio.run(main(jobs, os.path.abspath(sys.argv[2])))
