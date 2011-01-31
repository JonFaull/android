package net.cyclestreets.overlay;

import net.cyclestreets.R;
import net.cyclestreets.util.Brush;

import org.osmdroid.ResourceProxy;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class LocationOverlay extends MyLocationOverlay {
	public interface Callback {
		void onRouteNow(final GeoPoint from, final GeoPoint to);
		void onClearRoute();
	} // Callback

	private final Drawable greenWisp_;
	private final Drawable redWisp_;

	private final int offset_;
	private final float radius_;

	private final OverlayButton locationButton_;
	private final OverlayButton stepBackButton_;
	
	private final Callback callback_;
	
	private final GestureDetector gestureDetector_;
	private final MapView mapView_;
	
	private OverlayItem startItem_;
	private OverlayItem endItem_;

	private Paint textBrush_;

	private TapToRoute tapState_;
	
	public LocationOverlay(final Context context, 
						   final MapView mapView,
						   final Callback callback,
						   final ResourceProxy resProxy) 
	{
		super(context, mapView, resProxy);
		
		mapView_ = mapView;
		callback_ = callback;
		
		final Resources res = context.getResources();
		greenWisp_ = res.getDrawable(R.drawable.green_wisp_36x30);
		redWisp_ = res.getDrawable(R.drawable.red_wisp_36x30);

		offset_ = (int)(8.0 * context.getResources().getDisplayMetrics().density);		
		radius_ = offset_ / 2.0f;

		locationButton_ = new OverlayButton(res.getDrawable(android.R.drawable.ic_menu_mylocation),
											offset_,
											offset_,
											radius_);
        
        stepBackButton_ = new OverlayButton(res.getDrawable(android.R.drawable.ic_menu_revert),
        									locationButton_.right() + offset_,
        									offset_,
        									radius_);

		textBrush_ = Brush.createTextBrush(offset_);
        
		final SingleTapDetector tapDetector = new SingleTapDetector(this);
		gestureDetector_ = new GestureDetector(context, tapDetector);
		gestureDetector_.setOnDoubleTapListener(tapDetector);
		
		startItem_ = null;
		endItem_ = null;
		
		tapState_ = TapToRoute.start();
	} // LocationOverlay
	
	public void enableLocation(final boolean enable)
	{
		if(enable)
			enableMyLocation();
		else
			disableMyLocation();
	} // enableLocation
	
	public void setRoute(final GeoPoint start, final GeoPoint end, final boolean complete)
	{
		setStart(start);
		setEnd(end);

		tapState_ = tapState_.reset();
		if(start == null)
			return;		
		tapState_ = tapState_.next();
		if(end == null)
			return;
		tapState_ = tapState_.next();
		if(!complete)
			return;
		tapState_ = tapState_.next();
	} // setRoute
	
	public void resetRoute()
	{
		setStart(null);
		setEnd(null);
		tapState_ = tapState_.reset();
	} // resetRoute
	
	public GeoPoint getStart() { return getMarkerPoint(startItem_); }
	public GeoPoint getEnd() { return getMarkerPoint(endItem_); }

	private GeoPoint getMarkerPoint(final OverlayItem marker)
	{
		return marker != null ? marker.getPoint() : null;
	} // getMarkerPoint
	
	private void setStart(final GeoPoint point)
	{
		startItem_ = addMarker(point, "start", greenWisp_);
	} // setStart
	
	private void setEnd(final GeoPoint point)
	{
		endItem_ = addMarker(point, "finish", redWisp_);
	} // setEnd
	
	private OverlayItem addMarker(final GeoPoint point, final String label, final Drawable icon)
	{
		if(point == null)
			return null;
		final OverlayItem marker = new OverlayItem(label, label, point);
		marker.setMarker(icon);
		marker.setMarkerHotspot(new Point(0,30));
		return marker;
	} // addMarker

	////////////////////////////////////////////
	@Override
	public void onDraw(final Canvas canvas, final MapView mapView) {
		// I'm not thrilled about this but there isn't any other way (short of killing
		// and recreating the overlay) of turning off the little here-you-are man
		if(!isMyLocationEnabled())
			return;
		
		super.onDraw(canvas, mapView);
	} // onDraw
	
	@Override
	protected void onDrawFinished(final Canvas canvas, final MapView mapView) 
	{
        final Projection projection = mapView.getProjection();
        drawMarker(canvas, projection, startItem_);
        drawMarker(canvas, projection, endItem_);

		drawButtons(canvas);
		drawTapState(canvas);
	} // onDrawFinished
	
	private void drawButtons(final Canvas canvas)
	{
		locationButton_.draw(canvas);
		
		stepBackButton_.enable(tapState_ != TapToRoute.WAITING_FOR_START);
		stepBackButton_.draw(canvas);
	} // drawLocationButton

	private void drawTapState(final Canvas canvas)
	{
		final String msg = tapState_.toString();
		if(msg.length() == 0)
			return;
				
		final Rect screen = canvas.getClipBounds();
		final int halfWidth = screen.width() / 2;
        screen.left += halfWidth; 
        screen.top += offset_;
        screen.right -= offset_;
        screen.bottom = screen.top + locationButton_.height();
		
		drawRoundRect(canvas, screen, Brush.Grey);

		final Rect bounds = new Rect();
		textBrush_.getTextBounds(msg, 0, msg.length(), bounds);
		
		canvas.drawText(msg, screen.centerX(), screen.centerY() + bounds.bottom, textBrush_);
	} // drawTapState
	
	private void drawRoundRect(final Canvas canvas, final Rect rect, final Paint brush)
	{
		canvas.drawRoundRect(new RectF(rect), radius_, radius_, brush);
	} // drawRoundRect

	private void drawMarker(final Canvas canvas, 
							final Projection projection,
							final OverlayItem marker)
	{
		if(marker == null)
			return;
		final Point screenPos = new Point();
		projection.toMapPixels(marker.mGeoPoint, screenPos);

		final Drawable thingToDraw = marker.getDrawable();
		final int quarterWidth = thingToDraw.getIntrinsicWidth()/4;
		thingToDraw.setBounds(new Rect(screenPos.x - quarterWidth, 
									   screenPos.y - thingToDraw.getIntrinsicHeight(), 
									   screenPos.x + (quarterWidth*3), 
									   screenPos.y));
		thingToDraw.draw(canvas);
	} // drawMarker

	//////////////////////////////////////////////
	@Override
	public boolean onTouchEvent(final MotionEvent event, final MapView mapView)
	{
		if(gestureDetector_.onTouchEvent(event))
			return true;
		return super.onTouchEvent(event, mapView);
	} // onTouchEvent
	
    public boolean onSingleTapConfirmed(final MotionEvent event) {
    	return tapLocation(event) ||
    		   tapStepBack(event) || 
    		   tapMarker(event);
    } // onSingleTapUp
    
    public boolean onBackButton()
    {
    	return stepBack(false);
    } // onBackButton
    
	private boolean tapLocation(final MotionEvent event)
	{
		if(!locationButton_.hit(event))
			return false;
		
		if(!isMyLocationEnabled()) 
		{
			try {
				enableMyLocation();
				followLocation(true);
				final Location lastFix = getLastFix();
				if (lastFix != null)
					mapView_.getController().setCenter(new GeoPoint(lastFix));
			}
			catch(RuntimeException e) {
				// might not have location service
			}
		}
		else
		{
			followLocation(false);
			disableMyLocation();
		} // if ...
		
		mapView_.invalidate();

		return true;
	} // tapLocation
	
	private boolean tapStepBack(final MotionEvent event)
	{
		if(!stepBackButton_.hit(event))
			return false;

		return stepBack(true);
	} // tapStepBack
	
	private boolean stepBack(final boolean tap)
	{
		if(!tap && tapState_.isAtEnd())
			return false;
				
		switch(tapState_)
		{
    	case WAITING_FOR_START:
    		return true;
    	case WAITING_FOR_END:
    		setStart(null);
    		break;
    	case WAITING_TO_ROUTE:
    		setEnd(null);
    		break;
    	case ALL_DONE:
    		callback_.onClearRoute();
    		break;
    	} // switch ...
		
		mapView_.invalidate();
		tapState_ = tapState_.previous();
		
		return true;
	} // tapStepBack

    private boolean tapMarker(final MotionEvent event)
    {
    	final GeoPoint p = mapView_.getProjection().fromPixels((int)event.getX(), (int)event.getY());

		switch(tapState_)
    	{
    	case WAITING_FOR_START:
    		setStart(p);
    		mapView_.invalidate();
    		break;
    	case WAITING_FOR_END:
    		setEnd(p);
    		mapView_.invalidate();
    		break;
    	case WAITING_TO_ROUTE:
			callback_.onRouteNow(startItem_.getPoint(), endItem_.getPoint());
    		break;
    	case ALL_DONE:
    		break;
    	} // switch ...

    	tapState_ = tapState_.next();

    	return true;
    } // tapMarker
	
	////////////////////////////////////
	static private class SingleTapDetector extends GestureDetector.SimpleOnGestureListener
	{
		final private LocationOverlay owner_;
		SingleTapDetector(final LocationOverlay owner) { owner_ = owner; }
		
		@Override
		public boolean onSingleTapConfirmed(final MotionEvent event)
		{
			return owner_.onSingleTapConfirmed(event);
		} // onSingleTapConfirmed
	} // class SingleTapDetector
	
	private enum TapToRoute 
	{ 
		WAITING_FOR_START, 
		WAITING_FOR_END, 
		WAITING_TO_ROUTE, 
		ALL_DONE;
		
		static public TapToRoute start()
		{
			return WAITING_FOR_START;
		} // start
		
		public TapToRoute reset()
		{
			return WAITING_FOR_START;
		} // reset
		
		public boolean isAtEnd()
		{
			return (this == previous()) ||
				   (this == next());
		} // isAtEnd
		
		public TapToRoute previous() 
		{
			switch(this) {
			case WAITING_FOR_START:
				break;
			case WAITING_FOR_END:
				return WAITING_FOR_START;
			case WAITING_TO_ROUTE:
				return WAITING_FOR_END;
			case ALL_DONE:
				break;
			} // switch
			return WAITING_FOR_START;				
		} // previous()

		public TapToRoute next() 
		{
			switch(this) {
			case WAITING_FOR_START:
				return WAITING_FOR_END;
			case WAITING_FOR_END:
				return WAITING_TO_ROUTE;
			case WAITING_TO_ROUTE:
				return ALL_DONE;
			case ALL_DONE:
				break;
			} // switch
			return ALL_DONE;				
		} // next()
		
		public String toString()
		{
			switch(this) {
			case WAITING_FOR_START:
				return "Tap to set Start";
			case WAITING_FOR_END:
				return "Tap to set End";
			case WAITING_TO_ROUTE:
				return "Tap to plan route";
			case ALL_DONE:
				break;
			} // switch
			return "";				
		} // toString
	}; // enum TapToRoute
	
} // LocationOverlay