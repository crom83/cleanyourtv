"""Builds the screensaver map's data files from public-domain sources.

    curl -sSL -o countries.geojson https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_110m_admin_0_countries.geojson
    curl -sSL -o zone1970.tab https://raw.githubusercontent.com/eggert/tz/2024a/zone1970.tab
    python3 -I tools/build_map.py countries.geojson zone1970.tab \
        app/src/main/res/raw/world_countries.txt app/src/main/res/raw/zone_places.txt

world_countries.txt: one ring per line, "CC lon,lat lon,lat ...", CC the ISO 3166 code (ISO_A2_EH), rings
simplified with Douglas-Peucker at 0.25 degrees and rounded to 0.1 degrees; the app projects them (Equal Earth).
zone_places.txt: "Zone/Id lat lon" from zone1970.tab, for the lights on the cities.
"""
import json, sys, re
geo, tab, out_c, out_z = sys.argv[1:5]

def dp(pts, tol):
    if len(pts) < 3: return pts
    def d(p, a, b):
        (x,y),(x1,y1),(x2,y2)=p,a,b
        dx,dy=x2-x1,y2-y1
        if dx==0 and dy==0: return ((x-x1)**2+(y-y1)**2)**.5
        t=max(0,min(1,((x-x1)*dx+(y-y1)*dy)/(dx*dx+dy*dy)))
        return ((x-x1-t*dx)**2+(y-y1-t*dy)**2)**.5
    keep=[False]*len(pts); keep[0]=keep[-1]=True
    stack=[(0,len(pts)-1)]
    while stack:
        i,j=stack.pop(); best=-1; idx=-1
        for k in range(i+1,j):
            dd=d(pts[k],pts[i],pts[j])
            if dd>best: best,idx=dd,k
        if best>tol: keep[idx]=True; stack+= [(i,idx),(idx,j)]
    return [p for p,k in zip(pts,keep) if k]

data=json.load(open(geo))
lines=[]; n=0
for f in data['features']:
    p=f['properties']
    cc=p.get('ISO_A2_EH') or p.get('ISO_A2')
    if not cc or cc=='-99': cc=(p.get('ADM0_A3') or 'XX')[:2]
    if not re.fullmatch(r'[A-Z]{2}', cc): continue
    g=f['geometry']; polys=g['coordinates'] if g['type']=='MultiPolygon' else [g['coordinates']]
    for poly in polys:
        for ring in poly:
            r=dp([(round(x,1),round(y,1)) for x,y in ring],0.25)
            if len(r)<4: continue
            lines.append(cc+' '+' '.join(f'{x:g},{y:g}' for x,y in r)); n+=len(r)
open(out_c,'w').write('\n'.join(lines)+'\n')
def coord(s):
    m=re.fullmatch(r'([+-])(\d{2})(\d{2})(\d{2})?([+-])(\d{3})(\d{2})(\d{2})?', s)
    sg=lambda c:1 if c=='+' else -1
    lat=sg(m[1])*(int(m[2])+int(m[3])/60+int(m[4] or 0)/3600)
    lon=sg(m[5])*(int(m[6])+int(m[7])/60+int(m[8] or 0)/3600)
    return lat,lon
zl=[]
for line in open(tab):
    if line.startswith('#') or not line.strip(): continue
    cols=line.rstrip('\n').split('\t')
    lat,lon=coord(cols[1]); zl.append(f'{cols[2]} {lat:.1f} {lon:.1f}')
open(out_z,'w').write('\n'.join(zl)+'\n')
print(len(lines),'rings',n,'points',len(zl),'zones')
