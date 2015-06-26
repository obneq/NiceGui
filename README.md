NiceGui, a simple way to test and play your synthdefs
copyright 2015 obbneq - based on code by scott carver

example usage:

(
var synthdef;
synthdef={ arg out=0, gate=1, freq=333, noise = 0, a, d, s, r, ctf, res, amt, lvl;
	var sig, env;
	sig = Pulse.ar(freq * [1, 0.99922149]);
	sig = sig+WhiteNoise.ar(noise);
	env = EnvGen.kr(Env.adsr(a, d, s, r), gate, doneAction: 2);
	sig = sig * env;
	sig = RLPF.ar(sig, (ctf*2000)+(env*amt*1000), res);
	sig = sig * lvl;
	Out.ar(0, sig!2);
};

NiceGui.new(\testdef, synthdef).gui;
)