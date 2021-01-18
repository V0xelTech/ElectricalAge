package mods.eln.sixnode.wirelesssignal.rx;

import mods.eln.Eln;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.node.six.SixNodeDescriptor;
import mods.eln.node.six.SixNodeElementRender;
import mods.eln.node.six.SixNodeEntity;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;

public class WirelessSignalRxRender extends SixNodeElementRender {

    WirelessSignalRxDescriptor descriptor;

    boolean connection;

    String channel;
    int selectedAggregator;

    public WirelessSignalRxRender(SixNodeEntity tileEntity, Direction side, SixNodeDescriptor descriptor) {
        super(tileEntity, side, descriptor);
        this.descriptor = (WirelessSignalRxDescriptor) descriptor;
    }

    @Nullable
    @Override
    public CableRenderDescriptor getCableRender(@NotNull LRDU lrdu) {
        return Eln.instance.signalCableDescriptor.render;
    }

    @Override
    public void draw() {
        super.draw();

        drawSignalPin(new float[]{2, 2, 2, 2});
        front.glRotateOnX();
        descriptor.draw(connection);
    }

    @Nullable
    @Override
    public GuiScreen newGuiDraw(@NotNull Direction side, @NotNull EntityPlayer player) {
        return new WirelessSignalRxGui(this);
    }

    @Override
    public void publishUnserialize(DataInputStream stream) {
        super.publishUnserialize(stream);
        try {
            channel = stream.readUTF();
            connection = stream.readBoolean();
            selectedAggregator = stream.readByte();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
