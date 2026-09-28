package dev.hanks.vanilla;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.hanks.network.*;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.action.*;
import net.minecraft.server.level.ServerPlayer;

/** Player-bound social dialogs. Navigation never cancels selection or grabs the cursor. */
public final class PartyMenu {
    private enum Tab { SEARCH, RECENT, INVITES, MEMBER }
    private static final class Open {
        UUID token=UUID.randomUUID(); final UUID exit=UUID.randomUUID();
        final boolean returnToFighters;
        Tab tab=Tab.SEARCH; String query="",note="",signature=""; int page,lastClick=-100;
        UUID member; boolean typing; final Map<Integer,Runnable> actions=new HashMap<>();
        Open(boolean returnToFighters){this.returnToFighters=returnToFighters;}
    }
    private final VanillaSmash game;
    public final PartyDirectory recent=new PartyDirectory();
    private final Map<UUID,Open> open=new HashMap<>();
    private Map<UUID,Wire.OnlinePlayer> remote=Map.of(); private int remoteAt=-1000;
    public PartyMenu(VanillaSmash game){this.game=game;}
    public void presence(List<Wire.OnlinePlayer> online){
        var next=new HashMap<UUID,Wire.OnlinePlayer>();for(var p:online)next.put(p.id(),p);remote=Map.copyOf(next);remoteAt=game.ticks;
    }
    public boolean active(ServerPlayer p){return open.containsKey(p.getUUID());}
    public void forget(UUID id){if(open.remove(id)!=null)game.stage.partyFraming(id,false);}
    public void reset(){open.clear();recent.clear();remote=Map.of();remoteAt=-1000;}
    public void close(ServerPlayer p){if(active(p)){forget(p.getUUID());p.connection.send(ClientboundClearDialogPacket.INSTANCE);}}
    public void show(ServerPlayer p,boolean invitations){
        if(!game.hub.available(p))return;
        if(!game.uiPack.ready(p)){game.hub.notice(p.getUUID(),game.uiPack.status(p));return;}
        // The lobby is already a backdrop. Only an existing picker owns a preview
        // stage; opening Party from a command, hotbar or podium must not create one.
        boolean fromFighters=game.stage.active(p);
        game.rankings.close(p);
        if(!fromFighters)game.hub.results.hide(p.getUUID());
        game.fighterMenu.handoff(p);game.hub.menu.handoff(p);
        var o=open.computeIfAbsent(p.getUUID(),id->new Open(fromFighters));
        game.stage.partyFraming(p.getUUID(),true);
        if(invitations)o.tab=Tab.INVITES;
        paint(p,o,true);
    }
    private Map<UUID,Wire.OnlinePlayer> directory(){
        var all=new HashMap<UUID,Wire.OnlinePlayer>();
        if(game.ticks-remoteAt<100)all.putAll(remote);
        for(var p:game.server.getPlayerList().getPlayers())all.put(p.getUUID(),new Wire.OnlinePlayer(p.getUUID(),p.getPlainTextName(),game.hub.lastFighter(p).name(),!game.hub.available(p)));
        return all;
    }
    private PartyBook.View view(ServerPlayer p){return game.hub.parties.ensure(p.getUUID(),p.getPlainTextName());}
    private String status(UUID id,Map<UUID,Wire.OnlinePlayer> all){
        var person=all.get(id);if(person==null)return "Offline";if(person.inMatch())return "In match";
        var group=game.hub.parties.view(id);
        if(group!=null){if(group.phase()==PartyBook.Phase.PLAYING)return "In match";if(group.phase()==PartyBook.Phase.QUEUED)return "In queue";if(group.party())return "In party";}
        return "In lobby";
    }
    private void changeTab(ServerPlayer p,Open o,Tab tab){o.tab=tab;o.member=null;o.page=0;o.note="";paint(p,o,true);}
    private void back(ServerPlayer p){
        var o=open.get(p.getUUID());if(o==null)return;
        if(o.returnToFighters){forget(p.getUUID());game.fighterMenu.show(p);}
        else close(p);
    }
    private boolean available(ServerPlayer p,Open o){
        return game.hub.available(p) && (o.returnToFighters ? game.stage.active(p)
                : !game.stage.active(p) && p.level().dimension().equals(MvpWorlds.LOBBY));
    }
    public void tick(){
        if(game.ticks%20!=0)return;
        for(var entry:List.copyOf(open.entrySet())){
            var p=game.server.getPlayerList().getPlayer(entry.getKey());
            if(p==null){forget(entry.getKey());continue;}
            if(!available(p,entry.getValue())){close(p);continue;}
            if(!entry.getValue().typing)paint(p,entry.getValue(),false);
        }
    }
    private void paint(ServerPlayer p,Open o,boolean force){
        var party=view(p);var all=directory();var invitations=game.hub.parties.invites(p.getUUID(),game.ticks);
        o.actions.clear();
        o.actions.put(0,()->changeTab(p,o,Tab.SEARCH));o.actions.put(1,()->changeTab(p,o,Tab.RECENT));o.actions.put(2,()->changeTab(p,o,Tab.INVITES));
        o.actions.put(3,()->searchForm(p,o));o.actions.put(6,()->back(p));
        boolean leader=party.leader().equals(p.getUUID()),editable=party.phase()!=PartyBook.Phase.PLAYING;
        if(party.party()&&editable)o.actions.put(7,()->leave(p,o));
        for(int i=0;i<party.members().size();i++){
            var member=party.members().get(i);
            if(leader&&!member.id().equals(p.getUUID())&&editable)o.actions.put(10+i,()->{o.member=member.id();o.tab=Tab.MEMBER;o.note="";paint(p,o,true);});
        }
        var rows=new ArrayList<PartyCanvas.Row>();var rowIds=new ArrayList<UUID>();int total=0;
        if(o.tab==Tab.INVITES){
            total=invitations.size();o.page=Math.min(o.page,Math.max(0,(total-1)/3));
            for(var invite:invitations.stream().skip(o.page*3L).limit(3).toList()){
                rowIds.add(invite.party());
                int index=rows.size();var sender=all.get(invite.leader());var group=game.hub.parties.view(invite.leader());
                boolean canJoin=editable&&!party.party()&&party.phase()!=PartyBook.Phase.QUEUED&&group!=null&&group.members().size()<4&&group.phase()!=PartyBook.Phase.QUEUED&&group.phase()!=PartyBook.Phase.PLAYING&&sender!=null&&!sender.inMatch();
                if(canJoin)o.actions.put(20+index,()->accept(p,invite.party()));
                o.actions.put(30+index,()->{game.hub.parties.decline(p.getUUID(),invite.party());paint(p,o,true);});
                rows.add(new PartyCanvas.Row(invite.name(),sender==null?"STEVE":sender.fighter(),group==null?"Unavailable":group.members().size()+"/4 players",canJoin?"Join":"Busy",20+index,"Decline",30+index));
            }
        }else if(o.tab!=Tab.MEMBER){
            var contacts=o.tab==Tab.RECENT?recent.recent(p.getUUID()):PartyDirectory.search(all.values().stream().map(person->new PartyDirectory.Contact(person.id(),person.name(),person.fighter())).toList(),p.getUUID(),o.query);
            contacts=contacts.stream().filter(c->party.members().stream().noneMatch(m->m.id().equals(c.id()))).toList();
            total=contacts.size();o.page=Math.min(o.page,Math.max(0,(total-1)/3));
            for(var contact:contacts.stream().skip(o.page*3L).limit(3).toList()){
                rowIds.add(contact.id());
                int index=rows.size();String status=status(contact.id(),all);
                boolean sent=game.hub.parties.sent(p.getUUID(),contact.id(),game.ticks);
                boolean canInvite=leader&&editable&&party.phase()!=PartyBook.Phase.QUEUED&&party.members().size()<4&&!sent&&status.equals("In lobby")&&game.server.getPlayerList().getPlayer(contact.id())!=null;
                if(canInvite)o.actions.put(20+index,()->invite(p,contact.id()));
                String label=sent?"Sent":status.equals("Offline")?"Offline":!leader?"Leader":party.members().size()==4?"Full":canInvite?"Invite":"Busy";
                var latest=all.get(contact.id());rows.add(new PartyCanvas.Row(latest==null?contact.name():latest.name(),latest==null?contact.fighter():latest.fighter(),status,label,20+index,"",-1));
            }
        }
        PartyCanvas.Row selected=null;
        if(o.tab==Tab.MEMBER){
            var member=party.members().stream().filter(m->m.id().equals(o.member)).findFirst().orElse(null);
            if(member==null||!leader||!editable){o.tab=Tab.SEARCH;o.member=null;paint(p,o,true);return;}
            selected=new PartyCanvas.Row(member.name(),member.fighter(),member.ready()?"Ready":party.phase()==PartyBook.Phase.IDLE?"In lobby":"Selecting","",-1,"",-1);
            o.actions.put(40,()->manage(p,o,false));o.actions.put(41,()->manage(p,o,true));
        }
        int pages=Math.max(1,(total+2)/3);
        if(o.page>0)o.actions.put(4,()->{o.page--;paint(p,o,true);});
        if(o.page+1<pages)o.actions.put(5,()->{o.page++;paint(p,o,true);});
        // Compare the visible rows, including availability and outgoing invite expiry.
        // Unrelated players changing servers must not redraw this screen or invalidate a click.
        String signature=party+"/"+invitations+"/"+rows+"/"+rowIds+"/"+o.tab+"/"+o.query+"/"+o.page+"/"+pages+"/"+o.member+"/"+o.note+"/"+selected;
        if(!force&&signature.equals(o.signature))return;
        o.signature=signature;o.typing=false;o.token=UUID.randomUUID();
        var canvas=PartyCanvas.render(new PartyCanvas.State(party,invitations.size(),o.tab.name(),o.query,List.copyOf(rows),o.page,pages,o.note,selected,Set.copyOf(o.actions.keySet()),id->MenuActions.event(MenuActions.PARTY,o.token,id)));
        var json=base("Party");var message=new JsonObject();message.addProperty("type","minecraft:plain_message");message.addProperty("width",PartyCanvas.WIDTH+8);message.add("contents",encode(p,canvas));json.add("body",message);
        json.add("action",button(p,o.returnToFighters?"Back to fighters":"Back to lobby",false,MenuActions.dialogAction(MenuActions.PARTY,o.exit,6)));
        p.openDialog(Dialog.CODEC.parse(ops(p),json).getOrThrow());
    }
    private static net.minecraft.resources.RegistryOps<JsonElement> ops(ServerPlayer p){return p.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);}
    private static JsonElement encode(ServerPlayer p,Component value){return ComponentSerialization.CODEC.encodeStart(ops(p),value).getOrThrow();}
    private static JsonObject base(String title){var json=new JsonObject();json.addProperty("type","minecraft:notice");json.addProperty("title",title);json.addProperty("pause",false);json.addProperty("after_action","none");return json;}
    private static JsonObject button(ServerPlayer p,String label,boolean primary,JsonElement action){var button=new JsonObject();button.add("label",encode(p,PartyCanvas.nativeButton(label,primary)));button.addProperty("width",150);button.add("action",action);return button;}
    private void searchForm(ServerPlayer p,Open o){
        o.typing=true;o.token=UUID.randomUUID();o.actions.clear();
        var json=base("Find a player");json.addProperty("type","minecraft:multi_action");json.addProperty("columns",1);
        var input=new JsonObject();input.addProperty("key","query");input.addProperty("type","minecraft:text");input.addProperty("label","Minecraft username");input.addProperty("width",300);input.addProperty("max_length",16);input.addProperty("initial",o.query);
        var inputs=new JsonArray();inputs.add(input);json.add("inputs",inputs);
        var payload=new net.minecraft.nbt.CompoundTag();payload.putString("token",o.token.toString());payload.putInt("button",8);
        var submit=Action.CODEC.encodeStart(JsonOps.INSTANCE,new CustomAll(MenuActions.PARTY,Optional.of(payload))).getOrThrow();
        var actions=new JsonArray();actions.add(button(p,"Search",true,submit));json.add("actions",actions);
        o.actions.put(9,()->{o.typing=false;paint(p,o,true);});
        json.add("exit_action",button(p,"Cancel",false,MenuActions.dialogAction(MenuActions.PARTY,o.token,9)));
        p.openDialog(Dialog.CODEC.parse(ops(p),json).getOrThrow());
    }
    public void action(ServerPlayer p,UUID token,int button,String query){
        var o=open.get(p.getUUID());if(o==null||!available(p,o))return;
        if(button==6&&o.exit.equals(token)){back(p);return;}
        if(!o.token.equals(token)||game.ticks-o.lastClick<4)return;
        if(button==8&&o.typing){
            o.lastClick=game.ticks;
            try{o.query=PartyDirectory.query(query);o.tab=Tab.SEARCH;o.page=0;o.note="";}
            catch(IllegalArgumentException e){o.note=e.getMessage();}
            paint(p,o,true);return;
        }
        var action=o.actions.get(button);if(action==null)return;o.lastClick=game.ticks;o.token=UUID.randomUUID();
        try{action.run();}catch(IllegalStateException|IllegalArgumentException e){o.note=e.getMessage();paint(p,o,true);}
    }
    public void invite(ServerPlayer p,UUID target){
        var other=game.server.getPlayerList().getPlayer(target);
        if(other==null||!game.hub.available(other))throw new IllegalStateException("Player is no longer available");
        view(p);view(other);game.hub.parties.invite(p.getUUID(),target,game.ticks);
        game.hub.notice(target,p.getPlainTextName()+" invited you to a party");
        game.fighterMenu.refresh(other);game.fighterMenu.refresh(p);
        var o=open.get(p.getUUID());if(o!=null){o.note="Invitation sent";paint(p,o,true);}else show(p,false);
    }
    public void accept(ServerPlayer p,UUID party){
        var invitation=game.hub.parties.invites(p.getUUID(),game.ticks).stream().filter(i->i.party().equals(party)).findFirst().orElseThrow(()->new IllegalStateException("Invitation expired"));
        var leader=game.server.getPlayerList().getPlayer(invitation.leader());
        if(leader==null||!game.hub.available(leader))throw new IllegalStateException("Party is no longer available");
        game.hub.parties.accept(p.getUUID(),party,game.ticks);game.hub.results.book.leave(p.getUUID());
        var group=view(p);var contacts=group.members().stream().map(m->new PartyDirectory.Contact(m.id(),m.name(),m.fighter())).toList();
        for(var m:group.members())recent.met(m.id(),contacts);
        refresh(group);var o=open.get(p.getUUID());if(o!=null){o.tab=Tab.RECENT;o.note="Joined "+leader.getPlainTextName()+"'s party";paint(p,o,true);}
    }
    private void mutate(ServerPlayer p){if(!game.network.cancelSelection(p.getUUID()))throw new IllegalStateException("Your match is starting");}
    private void manage(ServerPlayer p,Open o,boolean kick){
        var previous=view(p);if(!previous.leader().equals(p.getUUID()))throw new IllegalStateException("Only the leader can do that");
        if(previous.members().stream().noneMatch(m->m.id().equals(o.member)))throw new IllegalStateException("Player left the party");
        mutate(p);
        if(kick){game.hub.parties.kick(p.getUUID(),o.member);game.hub.results.book.leave(o.member);var other=game.server.getPlayerList().getPlayer(o.member);if(other!=null){view(other);game.hub.notice(o.member,"You left the party");}}
        else game.hub.parties.promote(p.getUUID(),o.member);
        o.member=null;o.tab=Tab.SEARCH;o.note=kick?"Player removed":"Leader updated";refresh(previous);paint(p,o,true);
    }
    private void leave(ServerPlayer p,Open o){var previous=view(p);mutate(p);game.hub.parties.leave(p.getUUID());game.hub.results.book.leave(p.getUUID());view(p);o.tab=Tab.SEARCH;o.member=null;o.note="You left the party";refresh(previous);paint(p,o,true);}
    private void refresh(PartyBook.View group){for(var m:group.members()){var p=game.server.getPlayerList().getPlayer(m.id());if(p!=null)game.fighterMenu.refresh(p);}}
}
