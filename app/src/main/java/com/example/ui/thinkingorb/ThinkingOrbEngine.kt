package com.smartprocurement.internal.ui.thinkingorb

import kotlin.math.*

/**
 * Native geometry port of thinking-orbs 0.3.1 (commit de85557).  The public
 * composable paints this completed frame; all coordinates stay in logical
 * preset pixels here, so Android density cannot change the official tuning.
 */
enum class ThinkingOrbState {
    Working, Searching, Solving, Listening, Connecting, Weaving, Composing, Breathing, Shaping
}

internal enum class OrbPresetSize(val logicalPixels: Double) { Large(64.0), Inline(20.0) }

internal data class OrbPreset(val speed: Double, val count: Double, val markSize: Double, val extra: Map<String, Double> = emptyMap())
internal data class ResolvedOrbPreset(val mode: OrbMode, val speed: Double, val options: Map<String, Double>)
internal enum class OrbMode { Orbits, Globe, Rubik, Wave, Web, Braid, Ribbon, Ring, Morph }

/** Reusable primitive render buffers. Dots are z-sorted in place, far to near. */
internal class OrbFrameBuffer(capacity: Int = 4096) {
    private val x = DoubleArray(capacity)
    private val y = DoubleArray(capacity)
    private val z = DoubleArray(capacity)
    private val r = DoubleArray(capacity)
    private val white = DoubleArray(capacity)
    private val alpha = DoubleArray(capacity)
    private val x1 = DoubleArray(capacity)
    private val y1 = DoubleArray(capacity)
    private val x2 = DoubleArray(capacity)
    private val y2 = DoubleArray(capacity)
    private val lineWhite = DoubleArray(capacity)
    private val lineAlpha = DoubleArray(capacity)
    private val lineWidth = DoubleArray(capacity)
    var dotCount = 0
        private set
    var lineCount = 0
        private set

    fun clear() { dotCount = 0; lineCount = 0 }
    fun dot(px: Double, py: Double, pz: Double, radius: Double, ink: Double, a: Double = 1.0) {
        if (dotCount >= x.size) return
        val i = dotCount++
        x[i] = px; y[i] = py; z[i] = pz; r[i] = radius; white[i] = ink; alpha[i] = a
    }
    fun line(ax: Double, ay: Double, bx: Double, by: Double, ink: Double, a: Double, width: Double) {
        if (lineCount >= x1.size) return
        val i = lineCount++
        x1[i] = ax; y1[i] = ay; x2[i] = bx; y2[i] = by; lineWhite[i] = ink; lineAlpha[i] = a; lineWidth[i] = width
    }
    fun finalizeFrame(minRadius: Double) {
        var out = 0
        for (i in 0 until dotCount) if (alpha[i] >= 0.02) {
            if (out != i) copyDot(i, out)
            r[out] = max(minRadius, r[out])
            out++
        }
        dotCount = out
        out = 0
        for (i in 0 until lineCount) if (lineAlpha[i] >= 0.02) {
            if (out != i) copyLine(i, out)
            out++
        }
        lineCount = out
        if (dotCount > 1) sortDots(0, dotCount - 1)
    }
    private fun copyDot(from: Int, to: Int) { x[to]=x[from]; y[to]=y[from]; z[to]=z[from]; r[to]=r[from]; white[to]=white[from]; alpha[to]=alpha[from] }
    private fun copyLine(from: Int, to: Int) { x1[to]=x1[from]; y1[to]=y1[from]; x2[to]=x2[from]; y2[to]=y2[from]; lineWhite[to]=lineWhite[from]; lineAlpha[to]=lineAlpha[from]; lineWidth[to]=lineWidth[from] }
    private fun swapDot(a: Int, b: Int) {
        fun swap(array: DoubleArray) { val value=array[a]; array[a]=array[b]; array[b]=value }
        swap(x); swap(y); swap(z); swap(r); swap(white); swap(alpha)
    }
    private fun sortDots(low: Int, high: Int) {
        var lo = low; var hi = high
        val pivot = z[(low + high) ushr 1]
        while (lo <= hi) {
            while (z[lo] < pivot) lo++
            while (z[hi] > pivot) hi--
            if (lo <= hi) { swapDot(lo, hi); lo++; hi-- }
        }
        if (low < hi) sortDots(low, hi)
        if (lo < high) sortDots(lo, high)
    }
    fun dotX(i: Int)=x[i]; fun dotY(i: Int)=y[i]; fun dotZ(i: Int)=z[i]; fun dotRadius(i: Int)=r[i]; fun dotWhite(i: Int)=white[i]; fun dotAlpha(i: Int)=alpha[i]
    fun lineX1(i: Int)=x1[i]; fun lineY1(i: Int)=y1[i]; fun lineX2(i: Int)=x2[i]; fun lineY2(i: Int)=y2[i]; fun lineWhite(i: Int)=lineWhite[i]; fun lineAlpha(i: Int)=lineAlpha[i]; fun lineWidth(i: Int)=lineWidth[i]
}

internal object ThinkingOrbEngine {
    private const val Pi2 = Math.PI * 2.0
    private val base = mapOf(
        OrbMode.Globe to mapOf("latRings" to 17.0,"lonDensity" to 44.0,"rBase" to .6,"rDepth" to 1.7,"rBoost" to 1.0,"inkFar" to .62,"inkSpan" to .54,"rsPow" to .6,"rMin" to .3),
        OrbMode.Orbits to mapOf("orbitN" to 12.0,"ghostN" to 40.0,"ghostR" to .9,"ghostA" to .5,"particles" to 3.0,"partR" to 1.2,"partRDepth" to 1.6,"rsPow" to .6,"rMin" to .3),
        OrbMode.Rubik to mapOf("latRings" to 15.0,"lonDensity" to 40.0,"moveCount" to 14.0,"rBase" to .6,"rDepth" to 1.7,"rActive" to .3,"inkFar" to .62,"inkSpan" to .54,"rsPow" to .6,"rMin" to .3),
        OrbMode.Wave to mapOf("rings" to 15.0,"lonDensity" to 40.0,"rBase" to .6,"rDepth" to 1.7,"rsPow" to .6,"rMin" to .3),
        OrbMode.Web to mapOf("nodeN" to 30.0,"thr" to .72,"signals" to 5.0,"nodeR" to 1.4,"nodeRDepth" to 1.8,"lineW" to .8,"rsPow" to .6,"rMin" to .3),
        OrbMode.Braid to mapOf("strandN" to 52.0,"turns" to 3.0,"ghostN" to 150.0,"rBase" to 1.2,"rDepth" to 1.8,"rsPow" to .6,"rMin" to .3),
        OrbMode.Ribbon to mapOf("lanes" to 5.0,"segs" to 88.0,"ghostN" to 150.0,"rBase" to 1.1,"rDepth" to 1.7,"rsPow" to .6,"rMin" to .3),
        OrbMode.Ring to mapOf("lanes" to 5.0,"segs" to 88.0,"ghostN" to 0.0,"faceOn" to 1.0,"rBase" to 1.1,"rDepth" to 1.7,"rsPow" to .6,"rMin" to .3),
        OrbMode.Morph to mapOf("rDot" to .021,"iconD" to 1.0,"rMin" to .25)
    )
    private val modeForState = mapOf(
        ThinkingOrbState.Working to OrbMode.Orbits, ThinkingOrbState.Searching to OrbMode.Globe, ThinkingOrbState.Solving to OrbMode.Rubik,
        ThinkingOrbState.Listening to OrbMode.Wave, ThinkingOrbState.Connecting to OrbMode.Web, ThinkingOrbState.Weaving to OrbMode.Braid,
        ThinkingOrbState.Composing to OrbMode.Ribbon, ThinkingOrbState.Breathing to OrbMode.Ring, ThinkingOrbState.Shaping to OrbMode.Morph
    )
    private val presets = mapOf(
        OrbMode.Orbits to pair(1.885,1.0,1.0, 3.9,.238,2.4), OrbMode.Globe to pair(2.015,.42,1.15, 2.665,.105,1.75, mapOf("scanMul" to 4.08,"dimBase" to .45),mapOf("scanMul" to 4.335,"dimBase" to .45)),
        OrbMode.Rubik to pair(1.82,.35,1.05, 1.95,.088,1.9), OrbMode.Wave to pair(4.388,.341,1.0, 3.998,.105,1.6),
        OrbMode.Web to pair(3.315,1.35,.95, 6.63,.25,1.52), OrbMode.Braid to pair(1.625,.5,1.0, 2.75,.1125,1.36),
        OrbMode.Ribbon to pair(2.34,.25,.85, 3.12,.051,1.073,mapOf("spin" to 0.0,"bandMul" to 3.9,"wobMul" to 1.0),mapOf("spin" to 0.0,"bandMul" to 4.94,"wobMul" to 1.0)),
        OrbMode.Ring to pair(3.24,.25,.956, 3.78,.028,1.622,mapOf("spin" to 0.0,"bandMul" to 3.627,"wobMul" to .368),mapOf("spin" to 0.0,"bandMul" to 3.968,"wobMul" to .565)),
        OrbMode.Morph to pair(2.405,.702,.395, 2.08,.53,1.011,mapOf("spread" to 1.45),mapOf("spread" to 1.45))
    )
    private fun pair(a:Double,b:Double,c:Double,d:Double,e:Double,f:Double, extra64:Map<String,Double> = emptyMap(), extra20:Map<String,Double> = emptyMap()) =
        mapOf(OrbPresetSize.Large to OrbPreset(a,b,c,extra64), OrbPresetSize.Inline to OrbPreset(d,e,f,extra20))

    fun resolve(state: ThinkingOrbState, size: OrbPresetSize): ResolvedOrbPreset {
        val mode = modeForState.getValue(state); val preset = presets.getValue(mode).getValue(size)
        val out = base.getValue(mode).toMutableMap(); scaleCounts(out, preset.count); scaleRadii(out, preset.markSize); out.putAll(preset.extra)
        return ResolvedOrbPreset(mode, preset.speed, out)
    }
    private fun scaleCounts(o: MutableMap<String,Double>, scale: Double) {
        val rt=sqrt(scale)
        listOf("latRings" to "lonDensity","rings" to "lonDensity","lanes" to "segs").forEach { (a,b) -> if (o.containsKey(a)&&o.containsKey(b)) { o[a]=max(2.0,round(o.getValue(a)*rt)); o[b]=max(2.0,round(o.getValue(b)*rt)) } }
        listOf("orbitN","ghostN","nodeN","strandN","signals").forEach { key -> o[key]?.takeIf { it != 0.0 }?.let { o[key]=max(1.0,round(it*scale)) } }
        o["iconD"]?.let { o["iconD"]=max(.02,it*scale) }
    }
    private fun scaleRadii(o: MutableMap<String,Double>, scale: Double) {
        listOf("rBase","rDepth","rActive","rDot","ghostR","partR","partRDepth","nodeR","nodeRDepth").forEach { key -> o[key]?.let { o[key]=it*scale } }
        o["rSizeMul"]=(o["rSizeMul"] ?: 1.0)*scale
    }
    fun frame(state: ThinkingOrbState, size: OrbPresetSize, seconds: Double, output: OrbFrameBuffer) {
        val resolved=resolve(state,size); val t=seconds*resolved.speed; val logical=size.logicalPixels
        output.clear()
        when(resolved.mode) {
            OrbMode.Orbits -> orbits(logical,t,resolved.options,output); OrbMode.Globe -> globe(logical,t,resolved.options,output)
            OrbMode.Rubik -> rubik(logical,t,resolved.options,output); OrbMode.Wave -> wave(logical,t,resolved.options,output)
            OrbMode.Web -> web(logical,t,resolved.options,output); OrbMode.Braid -> braid(logical,t,resolved.options,output)
            OrbMode.Ribbon, OrbMode.Ring -> ribbon(logical,t,resolved.options,output); OrbMode.Morph -> morph(logical,t,resolved.options,output)
        }
        output.finalizeFrame(resolved.options["rMin"] ?: .3)
    }
    private fun Map<String,Double>.v(key:String, fallback:Double)=this[key] ?: fallback
    private fun rs(size:Double,o:Map<String,Double>)=(size/300.0).pow(o.v("rsPow",.6))
    private fun hash(a:Double,b:Double):Double { val h=sin(a*12.9898+b*78.233)*43758.5453; return h-floor(h) }
    private fun frac(x:Double)=x-floor(x)
    private fun noise(x:Double,y:Double):Double { val xi=floor(x); val yi=floor(y); var fx=x-xi; var fy=y-yi; fx=fx*fx*(3-2*fx); fy=fy*fy*(3-2*fy); val a=hash(xi,yi); val b=hash(xi+1,yi); val c=hash(xi,yi+1); val d=hash(xi+1,yi+1); return a+(b-a)*fx+(c-a)*fy+(a-b-c+d)*fx*fy }
    private fun adelta(a:Double,b:Double)=atan2(sin(a-b),cos(a-b))
    private class Projector(yaw:Double, tilt:Double, private val cx:Double, private val cy:Double, private val scale:Double) {
        private val st=sin(tilt); private val ct=cos(tilt); private val sy=sin(yaw); private val cyw=cos(yaw)
        fun into(x:Double,y:Double,z:Double,out:DoubleArray) { val x1=x*cyw+z*sy; val z1=-x*sy+z*cyw; val y1=y*ct-z1*st; out[0]=cx+x1*scale; out[1]=cy-y1*scale; out[2]=y*st+z1*ct }
    }
    private fun fib(i:Int,n:Int,out:DoubleArray) { val y=1-(2*(i+.5))/n; val rad=sqrt(1-y*y); val a=i*Math.PI*(3-sqrt(5.0)); out[0]=rad*cos(a);out[1]=y;out[2]=rad*sin(a) }

    private fun orbits(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) {
        val r=size*.41; val p=Projector(t*.12,.3,size/2,size/2,1.0); val q=DoubleArray(3); val scale=rs(size,o); val orbitN=o.v("orbitN",12.0).toInt(); val ghostN=o.v("ghostN",40.0).toInt(); val particles=o.v("particles",3.0).toInt()
        repeat(orbitN) { orb -> val h1=hash(orb.toDouble(),1.7); val h2=hash(orb.toDouble(),5.2); val h3=hash(orb.toDouble(),8.9); val ro=r*(.45+.52*h1); val th=h1*Pi2; val phi=acos(2*h2-1); val nx=sin(phi)*cos(th); val ny=cos(phi); val nz=sin(phi)*sin(th); var ux=-ny; var uy=nx; val ul=max(1e-6,sqrt(ux*ux+uy*uy)); ux/=ul;uy/=ul; val vx=-nz*uy; val vy=nz*ux; val vz=nx*uy-ny*ux; val speed=(.25+.55*h3)*if(h3>.5)1 else -1
            repeat(ghostN) { k -> val a=k.toDouble()/ghostN*Pi2; p.into((ux*cos(a)+vx*sin(a))*ro,(uy*cos(a)+vy*sin(a))*ro,(vz*sin(a))*ro,q); val depth=(q[2]/ro+1)/2; f.dot(q[0],q[1],q[2],o.v("ghostR",.9)*scale,.72,o.v("ghostA",.5)*(.4+.6*depth)) }
            repeat(particles) { m -> val a=t*speed+m.toDouble()/particles*Pi2+h2*6; p.into((ux*cos(a)+vx*sin(a))*ro,(uy*cos(a)+vy*sin(a))*ro,vz*sin(a)*ro,q); val depth=(q[2]/ro+1)/2; f.dot(q[0],q[1],q[2],(o.v("partR",1.2)+o.v("partRDepth",1.6)*depth)*scale,.3-.22*depth) }
        }
    }
    private fun globe(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) {
        val radius=size*.41; val spin=.5; val p=Projector(t*spin,.4+.06*sin(t*.35),size/2,size/2,radius); val q=DoubleArray(3); val scan=t*(spin+(1.7-spin)*o.v("scanMul",1.0)); val scale=rs(size,o); val rings=o.v("latRings",17.0).toInt(); val density=o.v("lonDensity",44.0).toInt()
        for(li in 0..rings) { val lat=-Math.PI/2+li.toDouble()/rings*Math.PI; val cl=cos(lat); val sl=sin(lat); val count=max(1,round(abs(cl)*density).toInt()); repeat(count) { lj -> val lon=lj.toDouble()/count*Pi2; p.into(cl*cos(lon),sl,cl*sin(lon),q); val depth=(q[2]+1)/2; val d=adelta(lon+t*spin,scan); val boost=exp(-(d*d)/.18)*max(0.0,q[2]); f.dot(q[0],q[1],q[2],(o.v("rBase",.6)+o.v("rDepth",1.7)*depth+o.v("rBoost",1.0)*boost)*scale,o.v("inkFar",.62)-o.v("inkSpan",.54)*depth,o.v("dimBase",1.0)+(1-o.v("dimBase",1.0))*min(1.0,boost)) } }
    }
    private data class Move(val axis:Int,val lo:Double,val hi:Double,val angle:Double)
    private fun moves(n:Int)=Array(n) { i -> val axis=min(2,floor(hash(i.toDouble(),2.3)*3).toInt()); val lo=-1+.5*min(3,floor(hash(i.toDouble(),5.9)*4).toInt()); Move(axis,lo,lo+.5,(if(hash(i.toDouble(),7.7)<.5)1 else -1)*Math.PI/2) }
    private fun solveCycle(t:Double,count:Int):Pair<DoubleArray,Int> { val slot=.42;val cyc=2*count*slot+1.2;val tc=t%cyc;val amount=DoubleArray(count);var active=-1;if(tc<2*count*slot){val current=floor(tc/slot).toInt();val progress=(tc-current*slot)/slot;val cl=min(1.0,progress/.7);val eased=1-(1-cl).pow(3);if(current<count){for(i in 0 until current)amount[i]=1.0;amount[current]=eased;active=current}else{val u=2*count-1-current;for(i in 0 until u)amount[i]=1.0;amount[u]=1-eased;active=u}};return amount to active }
    private fun rubik(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) {
        val r=size*.41;val p=Projector(t*.55,.35+.1*sin(t*.9),size/2,size/2,r);val q=DoubleArray(3);val scale=rs(size,o);val ms=moves(o.v("moveCount",14.0).toInt());val (amount,active)=solveCycle(t,ms.size);val rings=o.v("latRings",15.0).toInt();val density=o.v("lonDensity",40.0).toInt()
        for(li in 0..rings){val lat=-Math.PI/2+li.toDouble()/rings*Math.PI;val cl=cos(lat);val sl=sin(lat);val count=max(1,round(abs(cl)*density).toInt());repeat(count){lj->val lon=lj.toDouble()/count*Pi2;var x=cl*cos(lon);var y=sl;var z=cl*sin(lon);var moving=false;for(i in ms.indices){if(amount[i]<=0)continue;val m=ms[i];val coord=if(m.axis==0)x else if(m.axis==1)y else z;if(coord<m.lo||coord>=m.hi)continue;if(i==active)moving=true;val ca=cos(m.angle*amount[i]);val sa=sin(m.angle*amount[i]);when(m.axis){0->{val y2=y*ca-z*sa;z=y*sa+z*ca;y=y2};1->{val x2=x*ca+z*sa;z=-x*sa+z*ca;x=x2};else->{val x2=x*ca-y*sa;y=x*sa+y*ca;x=x2}}};p.into(x,y,z,q);val depth=(q[2]+1)/2;f.dot(q[0],q[1],q[2],(o.v("rBase",.6)+o.v("rDepth",1.7)*depth+(if(moving)o.v("rActive",.3)else 0.0))*scale,o.v("inkFar",.62)-o.v("inkSpan",.54)*depth-(if(moving).14 else 0.0))}}
    }
    private fun wave(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) { val r=size*.437;val p=Projector(t*.18,.38,size/2,size/2,1.0);val q=DoubleArray(3);val scale=rs(size,o);val rings=o.v("rings",15.0).toInt();val density=o.v("lonDensity",40.0).toInt();for(ri in 0..rings){val lat=-Math.PI/2+ri.toDouble()/rings*Math.PI;val cl=cos(lat);val sl=sin(lat);val w=.62*sin(t*2.1-ri*.52)+.38*sin(t*1.27+ri*.83);val rr=r*(.88+.105*w);val count=max(1,round(abs(cl)*density).toInt());repeat(count){lj->val lon=lj.toDouble()/count*Pi2;p.into(cl*cos(lon)*rr,sl*rr,cl*sin(lon)*rr,q);val depth=(q[2]/r+1)/2;val crest=max(0.0,w);f.dot(q[0],q[1],q[2],(o.v("rBase",.6)+o.v("rDepth",1.7)*depth)*(1+.4*crest)*scale,.66-.56*depth-.1*crest)}} }
    private fun web(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) { val r=size*.4*o.v("spread",1.0);val p=Projector(t*.12,.32,size/2,size/2,r);val q=DoubleArray(3);val q2=DoubleArray(3);val scale=rs(size,o);val n=o.v("nodeN",30.0).toInt();val nodes=Array(n){DoubleArray(3)};for(i in 0 until n){fib(i,n,nodes[i]);var x=nodes[i][0]+.3*(noise(i*.31+9,t*.24)-.5)*2;var y=nodes[i][1]+.3*(noise(i*.53+27,t*.21)-.5)*2;var z=nodes[i][2]+.3*(noise(i*.77+55,t*.27)-.5)*2;val l=sqrt(x*x+y*y+z*z);nodes[i][0]=x/l;nodes[i][1]=y/l;nodes[i][2]=z/l};val thr=o.v("thr",.72);for(i in 0 until n)for(j in i+1 until n){val dx=nodes[i][0]-nodes[j][0];val dy=nodes[i][1]-nodes[j][1];val dz=nodes[i][2]-nodes[j][2];val d=sqrt(dx*dx+dy*dy+dz*dz);if(d>=thr)continue;p.into(nodes[i][0],nodes[i][1],nodes[i][2],q);p.into(nodes[j][0],nodes[j][1],nodes[j][2],q2);val depth=((q[2]+q2[2])/2+1)/2;f.line(q[0],q[1],q2[0],q2[1],.42,(1-d/thr)*(.3+.55*depth),max(.6,o.v("lineW",.8)*scale))};val nodeR=o.v("nodeR",1.4);val nodeDepth=o.v("nodeRDepth",1.8);for(i in 0 until n){p.into(nodes[i][0],nodes[i][1],nodes[i][2],q);val depth=(q[2]+1)/2;f.dot(q[0],q[1],q[2],(nodeR+nodeDepth*depth)*(1+.25*sin(t*1.4+i*2.7))*scale,.55-.45*depth)};repeat(o.v("signals",5.0).toInt()){s->val seg=floor(t*.55+s*7.31);val a=floor(hash(seg,s*3.1+1.7)*n).toInt();val b=floor(hash(seg,s*5.7+4.2)*n).toInt();if(a==b)return@repeat;val k=frac(t*.55+s*7.31);var x=nodes[a][0]+(nodes[b][0]-nodes[a][0])*k;var y=nodes[a][1]+(nodes[b][1]-nodes[a][1])*k;var z=nodes[a][2]+(nodes[b][2]-nodes[a][2])*k;val l=max(1e-6,sqrt(x*x+y*y+z*z));p.into(x/l,y/l,z/l,q);val depth=(q[2]+1)/2;f.dot(q[0],q[1],q[2],(nodeR*1.5+nodeDepth*depth)*scale,.05,.5+.5*depth)} }
    private fun braid(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) { val r=size*.38;val p=Projector(t*.4,.3,size/2,size/2,1.0);val q=DoubleArray(3);val v=DoubleArray(3);val scale=rs(size,o);repeat(o.v("ghostN",150.0).toInt()){i->fib(i,o.v("ghostN",150.0).toInt(),v);p.into(v[0]*r,v[1]*r,v[2]*r,q);val depth=(q[2]/r+1)/2;f.dot(q[0],q[1],q[2],.8*scale,.78,.1+.22*depth)};val strand=o.v("strandN",52.0).toInt();val turns=o.v("turns",3.0);repeat(3){s->val phase=s/3.0*Pi2;repeat(strand){i->val u=(frac(i.toDouble()/strand+t*.045)*2-1)*.96;val surf=sqrt(max(0.0,1-u*u));val fade=min(1.0,(1-abs(u))/.1);val a=u*Math.PI*turns+phase;val weave=1+.075*sin(u*Math.PI*turns*2+phase*2+t*.8);val rr=surf*r*weave;p.into(cos(a)*rr,u*r*weave,sin(a)*rr,q);val depth=(q[2]/r+1)/2;f.dot(q[0],q[1],q[2],(o.v("rBase",1.2)+o.v("rDepth",1.8)*depth)*scale,.55-.45*depth,fade*(.45+.55*depth))}} }
    private fun ribbon(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) { val r=size*.39;val tilt=.3;val p=Projector(t*.1*o.v("spin",1.0),tilt,size/2,size/2,1.0);val q=DoubleArray(3);val v=DoubleArray(3);val scale=rs(size,o);val ghost=o.v("ghostN",150.0).toInt();repeat(ghost){i->fib(i,ghost,v);p.into(v[0]*r,v[1]*r,v[2]*r,q);val depth=(q[2]/r+1)/2;f.dot(q[0],q[1],q[2],.8*scale,.78,.1+.22*depth)};val spin=o.v("spin",1.0);val ya=t*.24*spin;val face=o["faceOn"] != null;val ta=if(face)-tilt else .55+.3*sin(t*.18)*spin;val ux=cos(ya);val uy=0.0;val uz=sin(ya);val vx=-uz*sin(ta);val vy=cos(ta);val vz=ux*sin(ta);val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx;val wobAmp=.23*o.v("wobMul",1.0);val baseR=if(face)r/(1+.85*wobAmp) else r;val lanes=max(1,round(o.v("lanes",5.0)*o.v("bandMul",1.0)).toInt());val segs=o.v("segs",88.0).toInt();for(w in 0 until lanes){val laneOff=(w-(lanes-1)/2.0)*.075;val edge=abs(w-(lanes-1)/2.0)/max(1.0,(lanes-1)/2.0);repeat(segs){k->val a=k.toDouble()/segs*Pi2;val wob=(.16*sin(a*3-t*1.7+w*.22)+.07*sin(a*5+t*1.1))*o.v("wobMul",1.0);val radial=if(face)1+wob else 1.0;val off=if(face)laneOff else laneOff+wob;val x=ux*cos(a)+vx*sin(a)+nx*off;val y=uy*cos(a)+vy*sin(a)+ny*off;val z=uz*cos(a)+vz*sin(a)+nz*off;val l=sqrt(x*x+y*y+z*z);p.into(x/l*baseR*radial,y/l*baseR*radial,z/l*baseR*radial,q);val depth=(q[2]/r+1)/2;f.dot(q[0],q[1],q[2],(o.v("rBase",1.1)+o.v("rDepth",1.7)*depth)*(1-.25*edge)*scale,.52-.44*depth+.18*edge,.4+.6*depth)}} }
    private fun morph(size:Double,t:Double,o:Map<String,Double>,f:OrbFrameBuffer) { val hold=1.4;val morph=.9;val seg=hold+morph;val tc=t%(seg*3);val k=floor(tc/seg).toInt();val local=tc-k*seg;val blend=if(local>hold){val x=(local-hold)/morph;x*x*(3-2*x)}else 0.0;val spread=o.v("spread",1.0);val px=DoubleArray(160);val py=DoubleArray(160);for(i in px.indices){val a=i/160.0;val a0=shape(k,a);val b=shape((k+1)%3,a);px[i]=(a0.first+(b.first-a0.first)*blend)*spread;py[i]=(a0.second+(b.second-a0.second)*blend)*spread};val lengths=DoubleArray(160);var total=0.0;for(i in px.indices){val j=(i+1)%px.size;lengths[i]=hypot(px[j]-px[i],py[j]-py[i]);total+=lengths[i]};val n=max(6,round(34*o.v("iconD",1.0)).toInt());val radius=max(.35,o.v("rDot",.021)*1.35*spread*size);val pulse=1+.02*sin(local*3.1);var index=0;var acc=0.0;repeat(n){point->val target=point.toDouble()/n*total;while(acc+lengths[index]<target&&index<159){acc+=lengths[index];index++};val next=(index+1)%160;val ratio=if(lengths[index]>0)min(1.0,(target-acc)/lengths[index])else 0.0;f.dot(size/2+(px[index]+(px[next]-px[index])*ratio)*pulse*size,size/2+(py[index]+(py[next]-py[index])*ratio)*pulse*size,0.0,radius,.1)} }
    private fun shape(kind:Int,f:Double):Pair<Double,Double> = when(kind){0->{val a=-Math.PI/2+f*Pi2;cos(a)*.24 to sin(a)*.24};1->poly(f, doubleArrayOf(0.0,-.26,.24,.16,-.24,.16));else->poly(f,doubleArrayOf(0.0,-.2,.2,-.2,.2,.2,-.2,.2,-.2,-.2))}
    private fun poly(f:Double, points:DoubleArray):Pair<Double,Double>{val n=points.size/2;val ls=DoubleArray(n);var total=0.0;for(i in 0 until n){val j=(i+1)%n;ls[i]=hypot(points[j*2]-points[i*2],points[j*2+1]-points[i*2+1]);total+=ls[i]};var target=f*total;var i=0;while(target>ls[i]&&i<n-1){target-=ls[i];i++};val j=(i+1)%n;val k=if(ls[i]>0)min(1.0,target/ls[i])else 0.0;return points[i*2]+(points[j*2]-points[i*2])*k to points[i*2+1]+(points[j*2+1]-points[i*2+1])*k}
}
