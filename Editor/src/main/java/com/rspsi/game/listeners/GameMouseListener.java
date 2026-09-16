package com.rspsi.game.listeners;

import com.jagex.Client;
import com.jagex.map.SceneGraph;
import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.input.PointerEventSink;

import javafx.event.EventHandler;
import javafx.scene.input.InputEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

public class GameMouseListener implements EventHandler<InputEvent> {

	private final Client client;
	private final PointerEventSink pointerEvents;

	public GameMouseListener(Client applet) {
		this(applet, null);
	}

	/** Optional neutral input path; null preserves the existing SceneGraph path. */
	public GameMouseListener(Client applet, PointerEventSink pointerEvents) {
		this.client = applet;
		this.pointerEvents = pointerEvents;
	}

	@Override
	public void handle(InputEvent event) {
		if(event instanceof ScrollEvent) {
			ScrollEvent scrollEvent = (ScrollEvent) event;
				if(scrollEvent.getEventType() == ScrollEvent.SCROLL) {
					if(scrollEvent.getDeltaY() >= 0) {
						for(int i = 0;i<3;i++)
						client.scrollIn();
					} else {
						for(int i = 0;i<3;i++)
						client.scrollOut();
					}
				}
			
		} else if(event instanceof MouseEvent) {
			MouseEvent mouseEvent = (MouseEvent) event;
			if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
				mousePressed(mouseEvent);
			} else if (event.getEventType() == MouseEvent.MOUSE_MOVED) {
				this.mouseMoved(mouseEvent);
			} else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
				this.mouseDragged(mouseEvent);
			} else if (event.getEventType() == MouseEvent.MOUSE_RELEASED) {
				this.mouseReleased(mouseEvent);
			} else if (event.getEventType() == MouseEvent.MOUSE_EXITED) {
				this.mouseExited();
			}
		}
		

	}

	public final void mouseDragged(MouseEvent event) {
		int x = (int) event.getX();
		int y = (int) event.getY();
		if (client.mouseWheelDown) {
			y = (int) (client.mouseWheelX - event.getX());
			int k = (int) (client.mouseWheelY - event.getY());
			client.mouseWheelDragged(y, -k);
			client.mouseWheelX = (int) event.getX();
			client.mouseWheelY = (int) event.getY();
			return;
		}
		client.mouseEventX = x;
		client.mouseEventY = y;
		client.metaModifierHeld = 3;

		SceneGraph.setMousePos(y, x);
		if (pointerEvents != null) {
			pointerEvents.pointerDrag(toPointerEvent(event));
		}
	}

	public final void mouseExited() {
		client.mouseEventX = -1;
		client.mouseEventY = -1;
	}

	public final void mouseMoved(MouseEvent event) {
		int x = (int) event.getX();
		int y = (int) event.getY();
		SceneGraph.setMousePos(y, x);
		client.mouseEventX = x;
		client.mouseEventY = y;
	}

	public final void mousePressed(MouseEvent event) {
		int x = (int) event.getX();
		int y = (int) event.getY();

		client.pressedX = x;
		client.pressedY = y;
		client.mouseClickTime = System.currentTimeMillis();
		if (event.getButton() == MouseButton.MIDDLE || event.getButton() == MouseButton.SECONDARY) {
			client.mouseWheelX = client.mouseEventX;
			client.mouseWheelY = client.mouseEventY;
			client.mouseWheelDown = true;
		} else if (event.isMetaDown()) {
			client.metaModifierPressed = 2;
			client.metaModifierHeld = 2;
		} else {
			client.metaModifierPressed = 1;
			client.metaModifierHeld = 1;

			SceneGraph.setMousePos(y, x);
			if (event.isPrimaryButtonDown()) {
				SceneGraph.setMouseIsDown(true);
			}
		}
		if (pointerEvents != null) {
			pointerEvents.pointerDown(toPointerEvent(event));
		}
	}

	public final void mouseReleased(MouseEvent event) {
		client.metaModifierHeld = 0;
		client.mouseWheelDown = false;

		SceneGraph.setMouseIsDown(false);
		if (pointerEvents != null) {
			pointerEvents.pointerUp(event == null
					? new PointerEvent(client.mouseEventX, client.mouseEventY, PointerButton.NONE,
					false, false, false)
					: toPointerEvent(event));
		}
	}

	/** Compatibility overload for callers that do not retain the JavaFX event. */
	public final void mouseReleased() {
		mouseReleased(null);
	}

	private PointerEvent toPointerEvent(MouseEvent event) {
		PointerButton button = switch (event.getButton()) {
			case PRIMARY -> PointerButton.PRIMARY;
			case SECONDARY -> PointerButton.SECONDARY;
			case MIDDLE -> PointerButton.MIDDLE;
			default -> event.isPrimaryButtonDown() ? PointerButton.PRIMARY
					: event.isSecondaryButtonDown() ? PointerButton.SECONDARY
					: event.isMiddleButtonDown() ? PointerButton.MIDDLE : PointerButton.NONE;
		};
		return new PointerEvent((float) event.getX(), (float) event.getY(), button,
				event.isShiftDown(), event.isControlDown(), event.isAltDown());
	}
}
