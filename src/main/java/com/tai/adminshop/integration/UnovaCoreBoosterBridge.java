package com.tai.adminshop.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.Method;
import java.util.OptionalLong;

/** Validated typed reflection boundary; no command string is accepted or executed. */
public final class UnovaCoreBoosterBridge {
    private static final String MOD_ID="unovacore_server", API="com.unova.core.booster.UnovaCoreBoosterApi";
    private static volatile Methods methods;private static volatile boolean resolved;
    private UnovaCoreBoosterBridge() { }
    public static boolean available(){Methods value=resolve();if(value==null)return false;try{return Boolean.TRUE.equals(value.available.invoke(null));}catch(Exception ignored){return false;}}
    public static OptionalLong priceSapphire(String id){Methods value=resolve();if(value==null)return OptionalLong.empty();try{return OptionalLong.of((long)value.price.invoke(null,id));}catch(Exception ignored){return OptionalLong.empty();}}
    public static OptionalLong durationSeconds(String id){Methods value=resolve();if(value==null)return OptionalLong.empty();try{return OptionalLong.of((long)value.duration.invoke(null,id));}catch(Exception ignored){return OptionalLong.empty();}}
    public static boolean activate(ServerPlayerEntity player,String id,int quantity){Methods value=resolve();if(value==null||player==null)return false;try{return Boolean.TRUE.equals(value.activate.invoke(null,player,id,quantity));}catch(Exception ignored){return false;}}
    private static Methods resolve(){if(methods!=null)return methods;if(resolved)return null;synchronized(UnovaCoreBoosterBridge.class){if(methods!=null)return methods;if(resolved)return null;resolved=true;if(!FabricLoader.getInstance().isModLoaded(MOD_ID))return null;try{Class<?> api=Class.forName(API,false,UnovaCoreBoosterBridge.class.getClassLoader());Method available=api.getMethod("available"),price=api.getMethod("priceSapphire",String.class),duration=api.getMethod("durationSeconds",String.class),activate=api.getMethod("activate",ServerPlayerEntity.class,String.class,int.class);if(available.getReturnType()!=boolean.class||price.getReturnType()!=long.class||duration.getReturnType()!=long.class||activate.getReturnType()!=boolean.class)return null;return methods=new Methods(available,price,duration,activate);}catch(ReflectiveOperationException|LinkageError ignored){return null;}}}
    private record Methods(Method available,Method price,Method duration,Method activate) { }
}
