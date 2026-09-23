import { useEffect, useState } from 'react';

function App() {
  const [greeting, setGreeting] = useState(null);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    fetch('/api/greeting')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Greeting request failed');
        }
        return response.json();
      })
      .then((data) => setGreeting(data.message))
      .catch(() => setHasError(true));
  }, []);

  return (
    <main>
      <h1>Coding Review with AI</h1>
      {hasError && <p>Unable to load greeting. Please try again.</p>}
      {!hasError && !greeting && <p>Loading greeting...</p>}
      {!hasError && greeting && <p>{greeting}</p>}
    </main>
  );
}

export default App;
