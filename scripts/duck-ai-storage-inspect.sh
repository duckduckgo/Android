  (async function inspect() {                                                                                                                                             
                                                                                                                                                                          
    // --- localStorage ---                                                                                                                                               
    console.group('localStorage');                                                                                                                                      
    const ls = {};
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);                                                                                                                                      
      ls[k] = localStorage.getItem(k);
    }                                                                                                                                                                     
    console.table(ls);                                                                                                                                                  
    console.groupEnd();                                                                                                                                                   
                                                                                                                                                                        
    // --- IndexedDB: list all databases ---                                                                                                                              
    console.group('IndexedDB databases');
    const dbs = await indexedDB.databases();                                                                                                                              
    console.log(dbs);                                                                                                                                                   
    console.groupEnd();
                                                                                                                                                                          
    // --- IndexedDB: dump savedAIChatData ---
    console.group('savedAIChatData');                                                                                                                                     
    await new Promise(resolve => {                                                                                                                                      
      const req = indexedDB.open('savedAIChatData');                                                                                                                      
      req.onerror = () => { console.warn('could not open savedAIChatData'); resolve(); };
      req.onsuccess = e => {                                                                                                                                              
        const db = e.target.result;                                                                                                                                     
        console.log('Object stores:', Array.from(db.objectStoreNames));                                                                                                   
                                                                                                                                                                        
        // saved-chats                                                                                                                                                    
        const tx1 = db.transaction('saved-chats', 'readonly');                                                                                                          
        tx1.objectStore('saved-chats').getAll().onsuccess = e => {                                                                                                        
          console.group('saved-chats (' + e.target.result.length + ' records)');                                                                                          
          console.log(e.target.result);                                                                                                                                   
          console.groupEnd();                                                                                                                                             
        };                                                                                                                                                                
                                                                                                                                                                        
        // chat-images (metadata only — file is a Blob, shown as {})                                                                                                      
        const tx2 = db.transaction('chat-images', 'readonly');
        tx2.objectStore('chat-images').getAll().onsuccess = e => {                                                                                                        
          console.group('chat-images (' + e.target.result.length + ' records)');                                                                                        
          console.log(e.target.result);                                                                                                                                   
          console.groupEnd();
          resolve();                                                                                                                                                      
        };                                                                                                                                                              
      };
    });
    console.groupEnd();
                                                                                                                                                                          
  })();
