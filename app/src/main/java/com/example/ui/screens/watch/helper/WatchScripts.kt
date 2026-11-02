package com.example.ui.screens.watch.helper

object WatchScripts {

    /**
     * Ad Overlay Cleaner Script
     * Safely detects and hides/removes intrusive click-jack overlays and transparent layers
     * while rigorously safeguarding real video elements and player controls.
     */
    val AD_CLEANER_JS = """
        (function() {
            if (window.__reeloCleanerInjected) return;
            window.__reeloCleanerInjected = true;

            function isProtected(el) {
                if (!el) return true;
                if (el.tagName === 'VIDEO' || el.tagName === 'AUDIO') return true;
                if (el.querySelector && el.querySelector('video, audio')) return true;
                
                // Common player containers and control bars
                const playerClasses = ['jw-', 'plyr', 'vjs-', 'video-js', 'player', 'controls', 'ytp-'];
                const cls = (typeof el.className === 'string') ? el.className.toLowerCase() : '';
                const id = (typeof el.id === 'string') ? el.id.toLowerCase() : '';
                
                for (let i = 0; i < playerClasses.length; i++) {
                    if (cls.includes(playerClasses[i]) || id.includes(playerClasses[i])) {
                        return true;
                    }
                }
                return false;
            }

            function cleanOverlays() {
                try {
                    const winW = window.innerWidth || document.documentElement.clientWidth || 1;
                    const winH = window.innerHeight || document.documentElement.clientHeight || 1;
                    const screenArea = winW * winH;

                    // Query elements that might be popups or overlays
                    const candidates = document.querySelectorAll('div, section, aside, iframe[src*="about:blank"]');
                    candidates.forEach(function(el) {
                        if (isProtected(el)) return;

                        const style = window.getComputedStyle(el);
                        if (!style) return;
                        if (style.display === 'none' || style.visibility === 'hidden') return;

                        const pos = style.position;
                        if (pos !== 'fixed' && pos !== 'absolute') return;

                        const zIndex = parseInt(style.zIndex, 10);
                        const rect = el.getBoundingClientRect();
                        const area = rect.width * rect.height;
                        const coverage = area / screenArea;

                        // Check 1: High z-index covering 30%+ of viewport
                        const isHugeOverlay = (zIndex >= 1000 || isNaN(zIndex)) && coverage >= 0.30;

                        // Check 2: Transparent click-catcher layer over video
                        const isTransparentTrap = (parseFloat(style.opacity) < 0.08 || style.backgroundColor === 'transparent' || style.backgroundColor === 'rgba(0, 0, 0, 0)') 
                            && (zIndex >= 900) && coverage >= 0.25;

                        if (isHugeOverlay || isTransparentTrap) {
                            // Look for a close button inside the overlay
                            const closeBtn = el.querySelector('[aria-label*="close" i], [title*="close" i], .close, #close, [class*="close" i], [class*="btn-close" i]');
                            if (closeBtn && !isProtected(closeBtn)) {
                                const text = (closeBtn.innerText || closeBtn.textContent || '').trim();
                                if (text === '×' || text === '✕' || text.toLowerCase() === 'x' || text.toLowerCase() === 'close') {
                                    try {
                                        closeBtn.click();
                                    } catch(e) {}
                                }
                            }

                            // If element is still visible or if transparent trap, disable and hide
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('pointer-events', 'none', 'important');
                            el.style.setProperty('visibility', 'hidden', 'important');
                        }
                    });
                } catch(e) {}
            }

            // Run throttled cleanups
            let throttleTimer = null;
            function triggerClean() {
                if (throttleTimer) return;
                throttleTimer = setTimeout(function() {
                    cleanOverlays();
                    throttleTimer = null;
                }, 500);
            }

            cleanOverlays();
            const observer = new MutationObserver(function() {
                triggerClean();
            });
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    cleanOverlays();
                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true });
                    }
                });
            }
        })();
    """.trimIndent()

    /**
     * Video Tracker & Playback Resume Script
     * Observes HTML5 <video> elements across main frame and iframes,
     * ignores short ad videos (< 120s), sends throttled timeupdates to ReeloBridge,
     * and listens for resume seek commands.
     */
    val VIDEO_RESUME_BRIDGE_JS = """
        (function() {
            if (window.__reeloResumeInjected) return;
            window.__reeloResumeInjected = true;

            let trackedVideo = null;
            let lastReportTime = 0;
            let hasReportedReady = false;

            function postToReelo(msgObj) {
                try {
                    const json = JSON.stringify(msgObj);
                    if (window.ReeloBridge && typeof window.ReeloBridge.postMessage === 'function') {
                        window.ReeloBridge.postMessage(json);
                    } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.ReeloBridge) {
                        window.webkit.messageHandlers.ReeloBridge.postMessage(json);
                    }
                } catch (e) {}
            }

            function setupVideoListener(video) {
                if (!video || video.__reeloHooked) return;
                video.__reeloHooked = true;

                function checkAndTrack() {
                    const dur = video.duration;
                    // Ignore short advertisement clips (<= 120 seconds)
                    if (dur && !isNaN(dur) && dur > 120) {
                        trackedVideo = video;
                        if (!hasReportedReady) {
                            hasReportedReady = true;
                            postToReelo({
                                type: 'ready',
                                duration: Math.floor(dur)
                            });
                        }
                    }
                }

                video.addEventListener('loadedmetadata', checkAndTrack);
                video.addEventListener('durationchange', checkAndTrack);
                checkAndTrack();

                video.addEventListener('timeupdate', function() {
                    const now = Date.now();
                    const dur = video.duration;
                    if (!dur || isNaN(dur) || dur <= 120) return;

                    trackedVideo = video;
                    if (now - lastReportTime >= 4500) {
                        lastReportTime = now;
                        postToReelo({
                            type: 'timeupdate',
                            currentTime: Math.floor(video.currentTime),
                            duration: Math.floor(dur),
                            ended: video.ended
                        });
                    }
                });

                video.addEventListener('ended', function() {
                    const dur = video.duration;
                    if (dur && dur > 120) {
                        postToReelo({
                            type: 'timeupdate',
                            currentTime: Math.floor(dur),
                            duration: Math.floor(dur),
                            ended: true
                        });
                    }
                });
            }

            function scanVideos() {
                const videos = document.querySelectorAll('video');
                for (let i = 0; i < videos.length; i++) {
                    setupVideoListener(videos[i]);
                }
            }

            scanVideos();
            const obs = new MutationObserver(function() {
                scanVideos();
            });

            if (document.body) {
                obs.observe(document.body, { childList: true, subtree: true });
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    scanVideos();
                    if (document.body) obs.observe(document.body, { childList: true, subtree: true });
                });
            }

            // Fallback timeout check if no video is found after 5s
            setTimeout(function() {
                if (!trackedVideo && !hasReportedReady) {
                    postToReelo({ type: 'no_video' });
                }
            }, 5000);

            // Handler for commands received from the native Android app
            window.__reeloExecuteCommand = function(cmdJson) {
                try {
                    const data = typeof cmdJson === 'string' ? JSON.parse(cmdJson) : cmdJson;
                    const v = trackedVideo || document.querySelector('video');
                    if (!v) return;

                    if (data.type === 'seek' && typeof data.time === 'number') {
                        const targetTime = data.time;
                        const dur = v.duration || 0;
                        if (targetTime > 10 && (!dur || targetTime < dur - 30)) {
                            v.currentTime = targetTime;
                        }
                    } else if (data.type === 'restart') {
                        v.currentTime = 0;
                    }
                } catch(e) {}
            };
        })();
    """.trimIndent()
}
