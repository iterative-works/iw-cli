
          document.addEventListener('visibilitychange', function() {
            if (document.visibilityState === 'visible') {
              htmx.trigger(document.body, 'refresh');
            }
          });
