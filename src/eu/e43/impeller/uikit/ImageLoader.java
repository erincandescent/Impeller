package eu.e43.impeller.uikit;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.support.v4.util.LruCache;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import com.google.common.collect.MapMaker;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.account.OAuth;
import eu.e43.impeller.uikit.AvatarView;

public class ImageLoader {
	static final String TAG = "ImageLoader";
	private Context m_ctx;
	private Account m_account;
    // Largest edge of display (i.e. bigger of width/height)
    //private int m_largestEdge;

    private static ExecutorService ms_threadpool;
    private static HashMap<URI, FetchTask> ms_tasks = new HashMap<URI, FetchTask>();
    // Needed to prevent issues when a ListView/etc recycles a View
    private static HashMap<View, URI> ms_viewUris = new HashMap<View, URI>();


	public interface Listener {
		public void loaded(BitmapDrawable dr, URI uri);
		public void error(URI uri);
	}
	
	public ImageLoader(Context ctx, Account acct) {
		m_ctx       = ctx;
		m_account	= acct;

        //Display disp = ctx.getWindowManager().getDefaultDisplay();
        //Point size = new Point();

        //disp.getSize(size);
        //m_largestEdge = size.x > size.y ? size.x : size.y;

        if(ms_threadpool == null) {
            ms_threadpool = Executors.newCachedThreadPool();
        }
	}
	
	// Utility Functions
	
	public void setImage(final ImageView view, URI uri, final Listener l) {
		view.setImageDrawable(m_ctx.getResources().getDrawable(R.drawable.ic_image_loading));
		ms_viewUris.put(view,  uri);
		load(new Listener() {
			@Override
			public void loaded(BitmapDrawable dr, URI uri) {
				if(uri.equals(ms_viewUris.get(view))) {
					view.setImageDrawable(dr);
					ms_viewUris.remove(uri);
				}
                if(l != null) l.loaded(dr, uri);
			}

			@Override
			public void error(URI uri) {
                URI viewUri = ms_viewUris.get(view);
				if(viewUri != null && uri != null && uri.equals(viewUri)) {
					view.setImageDrawable(m_ctx.getResources().getDrawable(R.drawable.ic_image_broken));
					ms_viewUris.remove(uri);
				}
                if(l != null) l.error(uri);
			}
		}, uri);
	}

    public void setImage(final ImageView view, URI uri) {
        setImage(view, uri, null);
    }

	public void setImage(ImageView view, String imageUrl, final Listener l) {
		URI uri;
		try {
			uri = new URI(imageUrl);
		} catch(Exception e) {
			uri = null;
		}
		setImage(view, uri, l);
	}

    public void setImage(ImageView view, String imageUrl) {
        setImage(view, imageUrl, null);
    }

    public void setImage(final AvatarView view, URI uri, final Listener l) {
        ms_viewUris.put(view,  uri);
        view.setAvatar(null);
        load(new Listener() {
            @Override
            public void loaded(BitmapDrawable dr, URI uri) {
                URI viewUri = ms_viewUris.get(view);
                if (viewUri != null && uri != null && uri.equals(viewUri)) {
                    view.setAvatar(dr.getBitmap());
                    ms_viewUris.remove(uri);
                }

                if(l != null) l.loaded(dr, uri);
            }

            @Override
            public void error(URI uri) {
                URI viewUri = ms_viewUris.get(view);
                if (viewUri != null && uri != null && uri.equals(viewUri)) {
                    ms_viewUris.remove(uri);
                }

                if(l != null) l.error(uri);
            }
        }, uri);
    }

    public void setImage(final AvatarView view, URI uri) {
        setImage(view, uri, null);
    }

    public void setImage(AvatarView view, String imageUrl, Listener l) {
        URI uri;
        try {
            uri = new URI(imageUrl);
        } catch(Exception e) {
            uri = null;
        }
        setImage(view, uri, l);
    }

    public void setImage(AvatarView view, String imageUrl) {
        setImage(view, imageUrl, null);
    }

    public void setBackground(final View view, URI uri) {
        ms_viewUris.put(view,  uri);
        load(new Listener() {
            @Override
            public void loaded(BitmapDrawable dr, URI uri) {
                URI viewUri = ms_viewUris.get(view);
                if (viewUri != null && uri != null && uri.equals(viewUri)) {
                    dr.setGravity(Gravity.CENTER | Gravity.FILL);
                    view.setBackgroundDrawable(dr);
                    ms_viewUris.remove(uri);
                }
            }

            @Override
            public void error(URI uri) {
                URI viewUri = ms_viewUris.get(view);
                if (viewUri != null && uri != null && uri.equals(viewUri)) {
                    ms_viewUris.remove(uri);
                }
            }
        }, uri);
    }

    public void setBackground(final View view, String imageUrl) {
        URI uri;
        try {
            uri = new URI(imageUrl);
        } catch(Exception e) {
            uri = null;
        }
        setBackground(view, uri);
    }

	public Drawable getCachedImage(URI uri) {
		return ms_images.get(uri);
	}
	
	public Drawable getCachedImage(String url) {
		try {
			return getCachedImage(new URI(url));
		} catch(URISyntaxException ex) {
            return null;
        } catch(NullPointerException ex) {
            return null;
        }
	}

    // Raw image loading

    public void load(Listener l, URI uri) {
        if(uri == null) {
            l.error(null);
            return;
        }

        BitmapDrawable dw = ms_images.get(uri);
        if(dw != null) {
            l.loaded(dw,  uri);
            return;
        }

        dw = ms_weakImages.get(uri);
        if(dw != null) {
            ms_images.put(uri, dw);
            l.loaded(dw, uri);
            return;
        }

        FetchTask task = ms_tasks.get(uri);
        if(task == null) {
            int largestEdge;
            if(m_ctx instanceof Activity) {
                Point size = new Point();
                ((Activity) m_ctx).getWindowManager().getDefaultDisplay().getRealSize(size);

                largestEdge = size.x > size.y ? size.x : size.y;
            } else {
                // Background - just needs to be big enough for flinging to Android Wear
                largestEdge = 512;
            }

            task = new FetchTask();
            ms_tasks.put(uri,  task);
            task.m_listeners.add(l);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                task.executeOnExecutor(ms_threadpool, uri, largestEdge);
            else
                task.execute(uri, largestEdge);
        } else {
            task.m_listeners.add(l);
        }
    }

    public void load(Listener l, String uri) {
        try {
            if(uri != null) {
                load(l, new URI(uri));
            } else {
                l.error(null);
            }
        } catch (Exception e) {
            l.error(null);
        }
    }

	class FetchTask extends AsyncTask<Object, Void, BitmapDrawable> {
		private URI 			   m_uri;
		public ArrayList<Listener> m_listeners = new ArrayList<Listener>();
        private int m_largestEdge;

        @Override
		protected BitmapDrawable doInBackground(Object... params) {
			m_uri         = (URI) params[0];
            m_largestEdge = (Integer) params[1];

			try {
				URL url = m_uri.toURL();
			
				HttpURLConnection conn = OAuth.fetchAuthenticated(m_ctx, m_account, url);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inDensity = 96;
                opts.inScaled = false;

                byte[] compressed = Utils.readAllBytes(conn.getInputStream());
                InputStream in = new ByteArrayInputStream(compressed);

                // Get the image bounds
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, opts);

                // Compute appropriate scale factor
                int largestEdge = opts.outWidth > opts.outHeight ? opts.outWidth : opts.outHeight;
                opts.inSampleSize = largestEdge / m_largestEdge;
                opts.inJustDecodeBounds = false;

                // Decode image. If we get OOM, try doubling the scale factor (blurry images are
                // better than none or crashing)
                Bitmap bmp = null;
                int attemptCount = 0;
                do {
                    try {
                        in.reset();
                        bmp = BitmapFactory.decodeStream(in, null, opts);
                        break;
                    } catch(OutOfMemoryError ex) {
                        attemptCount++;

                        if(opts.inSampleSize <= 1) {
                            opts.inSampleSize = 2;
                        } else {
                            opts.inSampleSize *= 2;
                        }
                    }
                } while(attemptCount < 5);

                if(bmp == null) {
                    Log.e(TAG, "Error decoding image " + url);
                    return null;
                }

                BitmapDrawable dw = new BitmapDrawable(m_ctx.getResources(), bmp);
				//dw.setBounds(0, 0, dw.getIntrinsicWidth(), dw.getIntrinsicHeight());
				return dw;
			} catch(Exception ex) {
				Log.e(TAG, "Error getting " + m_uri, ex);
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(BitmapDrawable dw) {
			ms_tasks.remove(m_uri);
			if(dw != null) {				
				ms_images.put(m_uri, dw);
				for(Listener l : m_listeners) {
					l.loaded(dw,  m_uri);
				}	
			} else {
				for(Listener l : m_listeners) {
					l.error(m_uri);
				}
			}
		}
		
	};

    static final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    static final int cacheSize = maxMemory / 8;
    static final ConcurrentMap<URI, BitmapDrawable> ms_weakImages = new MapMaker()
        .weakValues()
        .makeMap();

    static final LruCache<URI, BitmapDrawable> ms_images = new LruCache<URI, BitmapDrawable>(cacheSize) {
        @Override
        protected int sizeOf(URI key, BitmapDrawable value) {
            Bitmap bmp = value.getBitmap();
            if(bmp == null)
                return 0;

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                return bmp.getByteCount() / 1024;
            } else {
                return bmp.getRowBytes() * bmp.getHeight();
            }
        }

        @Override
        protected void entryRemoved(boolean evicted,
                                    URI key,
                                    BitmapDrawable oldValue,
                                    BitmapDrawable newValue) {
            if(evicted) {
                ms_weakImages.put(key, oldValue);
            } else {
                ms_weakImages.put(key, newValue);
            }
        }
    };
}
