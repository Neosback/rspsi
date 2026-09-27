package com.rspsi.editor.tool;
import com.rspsi.editor.brush.BrushEngine;
import com.rspsi.editor.brush.builtin.CircleBrush;
import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.tool.state.TilePainterState;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CompositeTilePainterToolTest {
 @Test void keepsJavaFacingConstructionIdentityAndInspector(){
  CompositeTilePainterTool tool=new CompositeTilePainterTool();
  assertEquals("tile-painter",tool.id()); assertEquals(6,tool.inspector().properties().size());
  assertEquals("underlayId",tool.inspector().properties().get(0).id()); assertEquals("brushRadius",tool.inspector().properties().get(5).id());
 }
 @Test void transformAppliesOnlyEnabledChannels(){
  CompositeTilePainterTool tool=new CompositeTilePainterTool();
  TileSnapshot before=new TileSnapshot(1,2,3,4,5,6,7,2,9,List.of(),TerrainHeightSource.unknown());
  tool.setApplyUnderlay(true);tool.setUnderlayId(11);tool.setApplyOverlay(true);tool.setOverlayId(12);tool.setApplyFlags(true);tool.setFlags(13);tool.setApplyHeight(true);tool.setHeight(128);
  TileSnapshot after=tool.transformTile(before);
  assertEquals(128,after.southWestHeight());assertEquals(128,after.northEastHeight());assertEquals(11,after.underlayId());assertEquals(12,after.overlayId());assertEquals(7,after.overlayShape());assertEquals(2,after.overlayRotation());assertEquals(13,after.flags());
 }
 @Test void bindingStateAndBrushRegistrationRemainLive(){
  TilePainterState first=new TilePainterState();BrushEngine engine=new BrushEngine();CompositeTilePainterTool tool=new CompositeTilePainterTool(first,engine);
  TilePainterState second=new TilePainterState();second.setBrushId("circle");tool.bindState(second);assertEquals("circle",tool.brush().id());
  CircleBrush circle=new CircleBrush();tool.setBrush(circle);assertSame(circle,tool.brush());assertEquals("circle",second.brushId());tool.setBrushRadius(4);assertEquals(4,second.brushRadius());
 }
}
