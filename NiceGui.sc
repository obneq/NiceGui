NiceGui {
	classvar <>folder = "/home/workflow/stuff/supercollider/NiceGui/";

	var server, <group, <presets;

	var <controlSpecs, <controls, guiElems;
	var <>currentPresetName, <>currentPresetDict, <>currentPreset;
	var <playingNotes, <currentlyPlaying=false, releaseDelay=0;

	var popup, buttons;
	var filepath, fileDict;

	var <>state=false; // true means edit gui!
	var synthname, synthdef, w;

	var modal;

	*new {
		|name, synthdef, server|
		^super.new.init(name, synthdef, server);
	}

	init {
		|name, def, s |
		var f;

		synthname = name;
		synthdef = def;

		filepath = folder ++ name ++ ".scd";
		server = server ? Server.default;

		if( File.exists(filepath), {
			"loading file!".postln;
			f = File.open( filepath, "r");
			fileDict = f.readAllString.interpret;
			f.close;
		}, {
			"new file!".postln;
			fileDict = (
				controls: Dictionary.new,
				presets:  Dictionary.new );

			state = true;
		});

		presets = fileDict[\presets].copy;
		controls = fileDict[\controls].copy;

		SynthDef(name, def).add.postln;

		guiElems = Dictionary.new;
		currentPresetDict = Dictionary.new;
		playingNotes = Dictionary.new;
		group = Group.new;

		this.uniGui;
	}

	dragControlView {
		| v, x, y |

		if(state, {
			var newPos;

			newPos = v.absoluteBounds - v.parent.absoluteBounds;
			newPos = (newPos.leftTop.asArray + [x, y]).div(5) * 5;

			controls[v.name.asSymbol].put(\pos, newPos);
			v.moveTo(*newPos);
		});
	}

	editControlView {
		| v, x, y, m, mb |
		var label, spec, p;

		if(mb != 1 || state.not, {^false});
		p = v.mapToGlobal(x@y);
		p.y = Window.screenBounds.height-p.y;

		if( modal.isNil, {
			modal = Window( "new preset name...", Rect(p.x, p.y, 200, 200), border:false )
			.onClose_({modal=nil})
			.alwaysOnTop_( true );

			View(modal, 200@60)
			.layout_(VLayout(
				StaticText(modal).string_("label"),
				label = TextField(w)
				.string_( controls[v.name.asSymbol][\label] ?? " ")
				.action_({ |p| this.edit(v, p, \label)})
			));
			View(modal, Rect(0, 50, 200, 60))
			.layout_(VLayout(
				StaticText(modal).string_("spec"),
				spec = TextField(modal)
				.string_( controls[v.name.asSymbol][\spec].asString ?? " ")
				.action_({ |p| p.postln; this.editSpec(v, p)})
			));
			View(modal, Rect(0, 100, 200, 60))
			.layout_(VLayout(
				StaticText(modal).string_("type"),
				PopUpMenu(modal).items_([ "knob", "vslider", "hslider", "button", "popup" ])
				.value_( controls[v.name.asSymbol][\type])
				.action_({ |p| this.edit(v, p, \type)})
			));
			Button( modal, Rect( 5, 175, 50, 14 ) )
			.action_({
				modal.close; modal=nil;
				label.doAction;
				spec.doAction;
				this.updateControl(v)})
			.states_([[ "Ok", Color.black, Color.clear]])
			.font_( Font("Helvetica",9));
			Button( modal, Rect( 60, 175, 50, 14 ) )
			.action_({ modal.close; modal=nil })
			.states_([[ "Cancel", Color.black, Color.clear]])
			.font_( Font("Helvetica",9));
			modal.front;
		})
	}

	edit {
		|v, p, sym|
		var name = v.name.asSymbol;

		controls[name][sym] = p.value;
	}

	editSpec {
		|v, p|
		var name = v.name.asSymbol;
		var spec = p.value;

		if(spec.value.isString, {spec = spec.value.interpret});
		controls[name][\spec] = spec;
	}

	addControl {
		| name |
		var knob, cclass, view = View(buttons).background_(Color.rand);

		// slider documentation says it will be horiz/vert automatically
		// depending on view size, but this does not work, so its explicit
		var horiz = false;

		switch( controls[name][\type],
			0, {view.bounds_(40@60);  cclass = Knob},
			1, {view.bounds_(40@190); cclass = Slider},
			2, {view.bounds_(175@60); cclass = Slider; horiz = true;},
			3, {view.bounds_(40@60);  cclass = Button},
			{view.bounds_(40@60); cclass=Knob});

		view.name_(name);
		view.layout_(
			VLayout(
				StaticText().string_(controls[name][\label].asString),
				knob = cclass.new
				.action_({ |k|
					var spec;

					// TODO: fix specs
					spec = controls[name][\spec].asSpec;

					currentPresetDict[name] = k.value;
					currentPreset = currentPresetDict.asKeyValuePairs;
					group.set( name, spec.map(k.value) );
				})
				.value_(currentPresetDict[name]);
				if(horiz, { knob.orientation_(\horizontal)});
				knob
			);
		);

		// don't click or move controls when editing gui
		knob.addAction({ if(state, { false }) }, \mouseDownAction);
		knob.addAction({ if(state, { false }) }, \mouseMoveAction);
		// controls ignore keys
		knob.addAction({ false }, \keyDownAction);
		guiElems[name] = knob;

		view.moveTo( *controls[name][\pos] );
		view.addAction({ |v, x, y| this.dragControlView(v, x, y)}, \mouseMoveAction);
		view.addAction({ |v, x, y, m, mb|
			this.editControlView(v, x, y, m, mb)}, \mouseDownAction);
	}

	updateControl {
		| view |
		var name = view.name.asSymbol;

		view.remove;
		this.addControl(name);
		group.set( name, guiElems[name].value );
	}

	uniGui {
		buttons = View();
		synthdef.argNames.do({
			| name |

			switch( name,
				// args without controls
				\out, {}, \gate, {}, \note, {}, \freq, {},
				// new default control
				{
					if(controls[name].isNil, {
						controls[name] = (
							type:  0,
							name:  name,
							label: name,
							pos:   [0, 0],
							spec:  [0, 1, 0.2]
						);
					});

					currentPresetDict[name] = 0.2;
					this.addControl(name);
			})
		});
		currentPreset = currentPresetDict.asKeyValuePairs;
	}

	savePresets {
		var f;
		// Write current preset
		presets[ currentPresetName ] = currentPresetDict;
		fileDict[\presets] = presets;
		// Save to file
		f = File.new( filepath, "w" );
		f.write( fileDict.asCompileString );
		f.close;
		"Presets saved...".postln;
	}

	saveGui {
		var f;
		fileDict[\controls] = controls;
		// Save to file
		f = File.new( filepath, "w" );
		f.write( fileDict.asCompileString );
		f.close;
		"Gui saved...".postln;
	}

	setState {
		state = not(state);
		if( not(state), { this.saveGui });
	}

	newPreset {
		| name |
		currentPresetName = name;
		presets[ name ] = currentPresetDict.copy;
		popup.items_( presets.keys.asArray );
		popup.value_( presets.keys.asArray.indexOfEqual( name ) );
	}

	allPresets {
		^this.presets.keys
	}

	setPreset {
		| preset |
		if( presets.includesKey( preset ), {
			currentPresetName = preset;
			currentPresetDict = presets[ preset ];
			if( controls.isNil.not, {
				currentPresetDict.keysValuesDo({
					| key, value |
					guiElems[key].value_( value );
				})
			});
			^currentPreset = currentPresetDict.asKeyValuePairs;
		},{ ^nil })
	}

	deletePreset {
		var num, keys;
		keys = presets.keys.asArray;
		num = keys.indexOfEqual( currentPresetName );
		num = (num-1).max(0);
		if( num.isNil, {num=0});
		presets.removeAt( currentPresetName );
		this.setPreset( presets.keys.asArray[num] );
		popup.items_( presets.keys.asArray );
		popup.value_( num );
	}

	randomPreset {
		var r = presets.values.size.rand;
		^this.setPreset( presets.keys.asArray[r].postln )
	}

	playNote {
		| notePitch=60, velocity=64, amp=0.3 |
		var synth;

		notePitch = notePitch.round(0.05);
		if( playingNotes[notePitch].isNil, {
			synth = Synth.new( synthname.asSymbol,
				currentPreset ++ [\freq, notePitch.midicps],
				group, \addToTail);
			playingNotes[notePitch] = synth;
		})
		^synth
	}

	playNotes {
		| notes, vel, amp=1.0 |
		notes.do({
			|notePitch|
			this.playNote( notePitch, vel, amp );
		})
	}

	stopNote {
		| notePitch |
		if(playingNotes.size<1, {^false});
		if( notePitch.size>0, {
			notePitch.do({
				| p |
				p = p.round(0.05);
				server.sendBundle(
					releaseDelay.max( 0.03 ),
					playingNotes[p].setMsg( \gate, 0 ); );
				playingNotes.removeAt(p);
			})
		}, {
			notePitch = notePitch.round(0.05);
			server.sendBundle(
				releaseDelay.max( 0.03 ),
				playingNotes[notePitch].setMsg( \gate, 0 ); );
			playingNotes.removeAt(notePitch);
		})
	}

	freeNote {
		| notePitch |
		if( notePitch.size>0, {
			notePitch.do({
				| p |
				p = p.round(0.05);

				playingNotes[p].free;
				playingNotes.removeAt(p);
			})
		}, {
			notePitch = notePitch.round(0.05);
			playingNotes[notePitch].free;
			playingNotes.removeAt(notePitch);
		})
	}

	stopAll {
		server.listSendMsg( group.setMsg(  \endIn, releaseDelay.max(0.03) ) );
		playingNotes = Dictionary.new;
	}

	freeAll {
		group.freeAll;
	}

	startMIDI {
		MIDIIn.connect;
		MIDIIn.noteOn = {
			|port, chan, note, vel|
		};
		MIDIIn.noteOff = {
			|port, chan, note, vel|
		};
	}

	gui {
		var newButton, modal;
		var font, keycodes, downkey;

		var top, del, save, kill, stat;

		font = Font( "Helvetica", 9 );

		w = Window( "Weedwacker", Rect(100, 345, 400, 655));
		// key note listener
		keycodes = [ 122, 115, 120, 100, 99, 118, 103, 98, 104,
			110, 106, 109, 44, 108, 46, 59, 127, 113, 50, 119,
			51, 101, 114, 53, 116, 54, 121, 55, 117, 105, 57, 111, 48, 112 ];

		// TODO: i have changed my SC Qt to ignore autorepeat notes, need to
		// figure out better solution
		w.view.keyDownAction_({
			| a,b,c,d |
			downkey = keycodes.indexOf( d );
			if( downkey.notNil, {
				if( true, { //currentlyPlaying, {
					this.playNote(60+downkey)
				})
			})
		});
		w.view.keyUpAction_({
			| a,b,c,d |
			downkey = keycodes.indexOf( d );
			if( downkey.notNil, {
				if( true, { //currentlyPlaying, {
					this.stopNote( 60+downkey )
				})
			})
		});

		w.view.background_( Color(0.80392156862745, 0.75294117647059, 0.69019607843137, 1.0) );

		w.onClose_({
			if( modal.notNil, { modal.close; modal=nil });
		});

		popup = PopUpMenu( bounds: Rect( 0, 0, 120, 20 ) )
		.items_( presets.keys.asArray)
		.value_( presets.keys.asArray.indexOfEqual( currentPresetName ) )
		.action_({
			| menu |
			var k = presets.keys.asArray;
			this.setPreset( k[menu.value]);
			w.view.focus(true);
		});
		// Preset Buttons
		newButton = Button( bounds: Rect( 0,0, "new".bounds(font).width+6, 16) )
		.states_( [["new", Color.black, Color.blue.alpha_(0.2)]] )
		.font_(font)
		.action_({
			var text, okButton;
			if( modal.isNil, {
				modal = Window( "new preset name...", Rect( 0, 0, 120, 50).moveToPoint( w.bounds.leftBottom + (newButton.bounds.leftTop*(1@(-1))) ) , border:false )
				.alwaysOnTop_( true );
				text = TextView( modal, Rect(5,5,100,14) )
				.string_("")
				.keyUpAction_({
					|a,b,c|
					if( b.ascii == 13, {
						okButton.doAction;
					})
				})
				.selectedString_("")
				.focus( true );
				okButton = Button( modal, Rect( 5, 25, 50, 14 ) )
				.action_({
					this.newPreset( text.string.asString );
					modal.close; modal=nil })
				.states_([[ "Ok", Color.black, Color.clear]])
				.font_( Font("Helvetica",9));
				Button( modal, Rect( 60, 25, 50, 14 ) )
				.action_({ modal.close; modal=nil })
				.states_([[ "Cancel", Color.black, Color.clear]])
				.font_( Font("Helvetica",9));
				modal.front;
			})
		});
		del = Button( bounds: Rect( 0,0, "delete".bounds(font).width+6, 16) )
		.states_( [["delete", Color.black, Color.blue.lighten(0.3).alpha_(0.2)]] )
		.font_(font)
		.action_({ this.deletePreset });
		save = Button( bounds: Rect( 0,0, "save".bounds(font).width+6, 16) )
		.states_( [["save", Color.black, Color.blue.lighten(0.3).alpha_(0.2)]] )
		.font_(font)
		.action_({ this.savePresets });
		kill = Button( bounds: Rect( 0,0, "kill all notes".bounds(font).width+6, 16) )
		.states_( [["kill all notes", Color.black, Color.red.lighten(0.3).alpha_(0.2)]] )
		.font_(font)
		.action_({ this.freeAll });
		stat = Button( bounds: Rect( 0,0, "xxxxxxx".bounds(font).width+6, 16) )
		.states_( [["fix"], ["ed"]] )
		.font_(font)
		.action_({ this.setState })
		.value_( state);

		top = HLayout(popup, newButton, del, save, kill, stat);
		w.layout_(VLayout(top, buttons));

		w.view.focus(true);
		^w.front
	}
}
