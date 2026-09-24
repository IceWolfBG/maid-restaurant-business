package com.icewolf.maidrestaurant.business.client.render;

// Auto-generated from sakefox_station.bbmodel. Do not edit by hand.
public final class JiuhuAnimData {
    private JiuhuAnimData() {}

    public static final float DURATION = 3.2f;
    public static final int B_GAP = 0;
    public static final int B_VOID = 1;
    public static final int B_LIDT = 2;
    public static final int B_LIDB = 3;
    public static final int B_SHARDS = 4;
    public static final int B_S0 = 5;
    public static final int B_S1 = 6;
    public static final int B_S2 = 7;
    public static final int B_DROPBAG = 8;
    public static final int B_FALL0 = 9;
    public static final int B_FALL1 = 10;
    public static final int B_FALL2 = 11;
    public static final int B_FALL3 = 12;

// render_proxy custom_model_data ids
public static final int CMD_VOID = 1;
public static final int CMD_LIDT = 2;
public static final int CMD_LIDB = 3;
public static final int CMD_S0 = 4;
public static final int CMD_S1 = 5;
public static final int CMD_S2 = 6;
public static final int CMD_SHARDS = 7;
public static final int CMD_DROPBAG = 8;
public static final int CMD_FALL0 = 9;
public static final int CMD_FALL1 = 10;
public static final int CMD_FALL2 = 11;
public static final int CMD_FALL3 = 12;
public static final int CMD_BAG1 = 13;
public static final int CMD_BAG2 = 14;
public static final int CMD_BAG3 = 15;
public static final int CMD_BAG4 = 16;
public static final int CMD_BAG5 = 17;

    // bone pivot origins in texture-pixel units (0..128 space)
public static final float[][] ORIGIN = {
    { 8f, 21.2f, 8f },
    { 8f, 21.2f, 8f },
    { 8f, 21.2f, 8f },
    { 8f, 21.2f, 8f },
    { 8f, 21.2f, 8f },
    { 2.9f, 22.6f, 8f },
    { 13.1f, 23.8f, 8f },
    { 3.3f, 18.6f, 8f },
    { 8f, 9.6f, 8f },
    { 4.9f, 24.3f, 8f },
    { 11.1f, 24.3f, 8f },
    { 4.9f, 18.1f, 8f },
    { 11.1f, 18.1f, 8f },
};

    // packed keyframes per bone: time,x,y,z, time,x,y,z ... ; null = no key
public static final float[][] ROT = {
    { 0f, 0f, 0f, 0f, 0.85f, 0f, 0f, 0f, 1.3f, 0f, 3.5f, 0f, 1.85f, 0f, -3.5f, 0f, 2.3f, 0f, 1.5f, 0f, 2.85f, 0f, 0f, 0f, 3.2f, 0f, 0f, 0f },
    null,
    { 0f, 0f, 0f, 0f, 0.35f, 0f, 0f, 0f, 0.6f, 24f, 0f, 0f, 0.9f, 16f, 0f, 0f, 1.85f, 16f, 0f, 0f, 2.05f, 30f, 0f, 0f, 2.5f, 12f, 0f, 0f, 2.75f, 0f, 0f, 0f, 3.2f, 0f, 0f, 0f },
    { 0f, 0f, 0f, 0f, 0.35f, 0f, 0f, 0f, 0.6f, -24f, 0f, 0f, 0.9f, -16f, 0f, 0f, 1.85f, -16f, 0f, 0f, 2.05f, -30f, 0f, 0f, 2.5f, -12f, 0f, 0f, 2.75f, 0f, 0f, 0f, 3.2f, 0f, 0f, 0f },
    { 0f, 0f, 0f, 0f, 1.6f, 0f, -10f, 0f, 3.2f, 0f, 0f, 0f },
    null,
    null,
    null,
    { 0f, 0f, 0f, 0f, 1.72f, 0f, 0f, 0f, 1.86f, 0f, 260f, 0f, 2.04f, 0f, 520f, 0f, 2.06f, 0f, 0f, 0f, 3.2f, 0f, 0f, 0f },
    null,
    null,
    null,
    null,
};

public static final float[][] POS = {
    { 0f, 0f, 0f, 0f, 0.35f, 0f, 0f, 0f, 0.45f, 0.08f, 0.2f, 0f, 0.55f, -0.08f, 0.35f, 0f, 0.7f, 0f, 0.45f, 0f, 1.6f, 0f, 0.5f, 0f, 2.2f, 0f, 0.45f, 0f, 2.62f, 0f, -0.12f, 0f, 2.85f, 0f, 0f, 0f, 3.2f, 0f, 0f, 0f },
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    { 0f, 0f, 11.6f, 1.1f, 0.9f, 0f, 11.6f, 1.1f, 1.02f, 0f, 11.1f, 0.2f, 1.2f, 0f, 11.6f, 0f, 1.4f, 0f, 11.74f, 0f, 1.58f, 0f, 11.52f, 0f, 1.72f, 0f, 11.95f, -0.3f, 1.9f, 0f, 11.7f, 0.55f, 2.12f, 0f, 11.6f, 1.1f, 3.2f, 0f, 11.6f, 1.1f },
    { 0f, -2.86f, 2.9f, -0.4f, 0.32f, -0.44f, 0.4f, -0.4f, 0.85f, 0f, 0f, 0f, 1.9f, 0f, 0f, 0f, 2.12f, -1.65f, 1.5f, -0.4f, 2.5f, -2.64f, 2.4f, -0.4f, 3.2f, -2.86f, 2.9f, -0.4f },
    { 0f, 2.86f, 2.9f, -0.4f, 0.32f, 0.44f, 0.4f, -0.4f, 0.85f, 0f, 0f, 0f, 1.9f, 0f, 0f, 0f, 2.12f, 1.65f, 1.5f, -0.4f, 2.5f, 2.64f, 2.4f, -0.4f, 3.2f, 2.86f, 2.9f, -0.4f },
    { 0f, -2.86f, -2.3f, -0.4f, 0.32f, -0.44f, -0.4f, -0.4f, 0.85f, 0f, 0f, 0f, 1.9f, 0f, 0f, 0f, 2.12f, -1.65f, -1.5f, -0.4f, 2.5f, -2.64f, -2.4f, -0.4f, 3.2f, -2.86f, -2.3f, -0.4f },
    { 0f, 2.86f, -2.3f, -0.4f, 0.32f, 0.44f, -0.4f, -0.4f, 0.85f, 0f, 0f, 0f, 1.9f, 0f, 0f, 0f, 2.12f, 1.65f, -1.5f, -0.4f, 2.5f, 2.64f, -2.4f, -0.4f, 3.2f, 2.86f, -2.3f, -0.4f },
};

public static final float[][] SCL = {
    { 0f, 0.01f, 0.01f, 0.01f, 0.35f, 0.01f, 0.01f, 0.01f, 0.5f, 1f, 1f, 1f, 0.68f, 1.06f, 1.06f, 1.06f, 0.85f, 1f, 1f, 1f, 2.5f, 1f, 1f, 1f, 2.62f, 0.92f, 0.92f, 0.92f, 2.78f, 0.35f, 0.35f, 0.35f, 2.9f, 0.01f, 0.01f, 0.01f, 3.2f, 0.01f, 0.01f, 0.01f },
    { 0f, 1f, 0.02f, 1f, 0.35f, 1f, 0.02f, 1f, 0.48f, 1f, 0.35f, 1f, 0.62f, 1f, 1.1f, 1f, 0.8f, 1f, 1f, 1f, 2.35f, 1f, 1f, 1f, 2.55f, 1f, 0.3f, 1f, 2.7f, 1f, 0.05f, 1f, 3.2f, 1f, 0.02f, 1f },
    null,
    null,
    { 0f, 1f, 1f, 1f, 0.85f, 0.85f, 0.85f, 0.85f, 1.6f, 1.08f, 1.08f, 1.08f, 2.6f, 0.85f, 0.85f, 0.85f, 3.2f, 1f, 1f, 1f },
    { 0f, 0.01f, 0.01f, 0.01f, 0.5f, 0.01f, 0.01f, 0.01f, 0.68f, 1f, 1f, 1f, 1.05f, 1f, 1f, 1f, 1.3f, 0.01f, 0.01f, 0.01f, 3.2f, 0.01f, 0.01f, 0.01f },
    { 0f, 0.01f, 0.01f, 0.01f, 0.75f, 0.01f, 0.01f, 0.01f, 0.93f, 1f, 1f, 1f, 1.35f, 1f, 1f, 1f, 1.65f, 0.01f, 0.01f, 0.01f, 3.2f, 0.01f, 0.01f, 0.01f },
    { 0f, 0.01f, 0.01f, 0.01f, 0.95f, 0.01f, 0.01f, 0.01f, 1.13f, 1f, 1f, 1f, 1.6f, 1f, 1f, 1f, 1.9f, 0.01f, 0.01f, 0.01f, 3.2f, 0.01f, 0.01f, 0.01f },
    { 0f, 0.01f, 0.01f, 0.01f, 0.9f, 0.01f, 0.01f, 0.01f, 1.08f, 1.15f, 1.15f, 1.15f, 1.25f, 1f, 1f, 1f, 1.7f, 0.95f, 1.05f, 0.95f, 1.85f, 0.4f, 0.4f, 0.4f, 2f, 0.05f, 0.05f, 0.05f, 2.12f, 0.01f, 0.01f, 0.01f, 3.2f, 0.01f, 0.01f, 0.01f },
    { 0f, 0.05f, 0.05f, 0.05f, 0.32f, 0.5f, 0.5f, 0.5f, 0.9f, 0.9f, 0.9f, 0.9f, 1.9f, 0.95f, 0.95f, 0.95f, 2.2f, 0.5f, 0.5f, 0.5f, 2.6f, 0.05f, 0.05f, 0.05f, 3.2f, 0.05f, 0.05f, 0.05f },
    { 0f, 0.05f, 0.05f, 0.05f, 0.32f, 0.5f, 0.5f, 0.5f, 0.9f, 0.9f, 0.9f, 0.9f, 1.9f, 0.95f, 0.95f, 0.95f, 2.2f, 0.5f, 0.5f, 0.5f, 2.6f, 0.05f, 0.05f, 0.05f, 3.2f, 0.05f, 0.05f, 0.05f },
    { 0f, 0.05f, 0.05f, 0.05f, 0.32f, 0.5f, 0.5f, 0.5f, 0.9f, 0.9f, 0.9f, 0.9f, 1.9f, 0.95f, 0.95f, 0.95f, 2.2f, 0.5f, 0.5f, 0.5f, 2.6f, 0.05f, 0.05f, 0.05f, 3.2f, 0.05f, 0.05f, 0.05f },
    { 0f, 0.05f, 0.05f, 0.05f, 0.32f, 0.5f, 0.5f, 0.5f, 0.9f, 0.9f, 0.9f, 0.9f, 1.9f, 0.95f, 0.95f, 0.95f, 2.2f, 0.5f, 0.5f, 0.5f, 2.6f, 0.05f, 0.05f, 0.05f, 3.2f, 0.05f, 0.05f, 0.05f },
};
}