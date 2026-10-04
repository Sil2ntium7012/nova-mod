# 49-270차: 노바 치장품(날개 등) 묶기 - 그림(png) + 모델(geo.json) + 애니메이션(animation.json)을 암호화해 jar에 넣을 .bin 하나로.
#   py tools\pack-cosmetic.py butterfly 그림.png 모델.geo.json 애니메이션.animation.json
#   → src/main/resources/assets/lunaslight/cos/<이름 해시>.bin
# jar를 풀어도 png, json이 안 나온다(게임 안에서만 메모리에서 풀린다 - util/NovaWings). 필요: pip install cryptography
# 키는 NovaWings.K 와 같은 값(마스크를 씌워 나눠 둠)이다 - 한쪽만 바꾸면 못 푼다.
import sys, os, hashlib, struct, base64
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PARTS = ["zAMSYGdn+zy0Z/O", "7+ARsOsy+zpQf+b", "1BkVSuKJuiNTg="]

def key():
    masked = base64.b64decode("".join(PARTS))
    mask = hashlib.sha256(b"NovaClient|cos|mask|49-270").digest()
    return bytes(a ^ b for a, b in zip(masked, mask))

def main():
    if len(sys.argv) != 5:
        print(__doc__ or "usage: pack-cosmetic.py <key> <png> <geo.json> <animation.json>")
        sys.exit(1)
    name, png, geo, anim = sys.argv[1:]
    blobs = [open(p, "rb").read() for p in (png, geo, anim)]
    body = b"NWC1" + b"".join(struct.pack(">I", len(x)) + x for x in blobs)
    iv = os.urandom(12)
    aad = ("nova-cos|" + name).encode()
    out = iv + AESGCM(key()).encrypt(iv, body, aad)
    fname = hashlib.sha256(("nova-cos|" + name).encode()).hexdigest()[:16] + ".bin"
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "lunaslight", "cos")
    os.makedirs(root, exist_ok=True)
    path = os.path.join(root, fname)
    open(path, "wb").write(out)
    print("ok:", os.path.normpath(path), len(out), "bytes")

if __name__ == "__main__":
    main()
