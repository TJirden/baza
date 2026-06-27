import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
  vus: 50,
  duration: '10s',
};

export default function () {
  http.get('http://localhost:8080/api/memes/search?q=test');
  sleep(1);
}
